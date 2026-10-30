#include "hook_shader.h"

#include <cctype>
#include <cmath>
#include <cstdlib>
#include <sstream>
#include <vector>

namespace a4k {

namespace {

std::string trim(const std::string &text) {
    size_t start = text.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return {};
    size_t end = text.find_last_not_of(" \t\r\n");
    return text.substr(start, end - start + 1);
}

bool startsWith(const std::string &text, const char *prefix) {
    return text.rfind(prefix, 0) == 0;
}

}  // namespace

bool parseHookShader(const std::string &source, std::vector<HookPass> *passes, std::string *error) {
    std::istringstream lines(source);
    std::string line;
    HookPass current;
    bool inPass = false;
    bool inBody = false;

    auto finish = [&]() {
        if (inPass) passes->push_back(current);
        current = HookPass();
        inPass = false;
        inBody = false;
    };

    while (std::getline(lines, line)) {
        std::string clean = trim(line);
        if (startsWith(clean, "//!")) {
            // A directive after body lines starts the next pass.
            if (inBody) finish();
            inPass = true;
            std::string directive = clean.substr(3);
            size_t space = directive.find_first_of(" \t");
            std::string key = space == std::string::npos ? directive : directive.substr(0, space);
            std::string value = space == std::string::npos ? "" : trim(directive.substr(space + 1));
            if (key == "DESC") {
                current.desc = value;
            } else if (key == "HOOK") {
                if (!current.hook.empty()) {
                    *error = "more than one HOOK in a pass is not supported: " + current.desc;
                    return false;
                }
                current.hook = value;
            } else if (key == "BIND") {
                current.binds.push_back(value);
            } else if (key == "SAVE") {
                current.save = value;
            } else if (key == "WIDTH") {
                current.width = value;
            } else if (key == "HEIGHT") {
                current.height = value;
            } else if (key == "WHEN") {
                current.when = value;
            } else if (key == "COMPONENTS") {
                current.components = std::atoi(value.c_str());
            } else if (key == "COMPUTE") {
                int sizes[4] = {0, 0, 0, 0};
                std::istringstream numbers(value);
                int count = 0;
                while (count < 4 && numbers >> sizes[count]) ++count;
                if (count != 2 && count != 4) {
                    *error = "bad //!COMPUTE in " + current.desc;
                    return false;
                }
                current.compute = true;
                current.blockWidth = sizes[0];
                current.blockHeight = sizes[1];
                current.threadsWidth = count == 4 ? sizes[2] : sizes[0];
                current.threadsHeight = count == 4 ? sizes[3] : sizes[1];
                if (current.blockWidth < 1 || current.blockHeight < 1 || current.threadsWidth < 1 || current.threadsHeight < 1) {
                    *error = "bad //!COMPUTE in " + current.desc;
                    return false;
                }
            } else {
                *error = "unsupported directive //!" + key;
                return false;
            }
        } else if (inPass) {
            inBody = true;
            current.body += line;
            current.body += '\n';
        }
    }
    finish();
    for (const auto &pass : *passes) {
        if (pass.hook.empty()) {
            *error = "pass without HOOK: " + pass.desc;
            return false;
        }
    }
    return true;
}

bool evalExpression(const std::string &expression, const SizeLookup &lookup, float *result, std::string *error) {
    std::istringstream tokens(expression);
    std::string token;
    std::vector<float> stack;
    auto pop = [&](float *value) {
        if (stack.empty()) return false;
        *value = stack.back();
        stack.pop_back();
        return true;
    };
    while (tokens >> token) {
        if (token.size() == 1 && std::string("+-*/%<>=!").find(token[0]) != std::string::npos) {
            char op = token[0];
            float b = 0, a = 0;
            if (op == '!') {
                if (!pop(&a)) break;
                stack.push_back(a == 0.0f ? 1.0f : 0.0f);
                continue;
            }
            if (!pop(&b) || !pop(&a)) {
                *error = "stack underflow in \"" + expression + "\"";
                return false;
            }
            switch (op) {
                case '+': stack.push_back(a + b); break;
                case '-': stack.push_back(a - b); break;
                case '*': stack.push_back(a * b); break;
                case '/': stack.push_back(a / b); break;
                case '%': stack.push_back(std::fmod(a, b)); break;
                case '<': stack.push_back(a < b ? 1.0f : 0.0f); break;
                case '>': stack.push_back(a > b ? 1.0f : 0.0f); break;
                case '=': stack.push_back(a == b ? 1.0f : 0.0f); break;
            }
            continue;
        }
        char *end = nullptr;
        float number = std::strtof(token.c_str(), &end);
        if (end != token.c_str() && *end == '\0') {
            stack.push_back(number);
            continue;
        }
        size_t dot = token.rfind('.');
        if (dot == std::string::npos) {
            *error = "unknown token \"" + token + "\"";
            return false;
        }
        std::string name = token.substr(0, dot);
        std::string field = token.substr(dot + 1);
        float w = 0, h = 0;
        if (!lookup(name, &w, &h)) {
            *error = "unknown texture \"" + name + "\" in \"" + expression + "\"";
            return false;
        }
        if (field == "w" || field == "width") {
            stack.push_back(w);
        } else if (field == "h" || field == "height") {
            stack.push_back(h);
        } else {
            *error = "unknown field \"" + token + "\"";
            return false;
        }
    }
    if (stack.size() != 1) {
        *error = "bad expression \"" + expression + "\"";
        return false;
    }
    *result = stack.back();
    return true;
}

namespace {

long long countOf(const std::string &body, const std::string &needle) {
    long long count = 0;
    size_t at = 0;
    while ((at = body.find(needle, at)) != std::string::npos) {
        // "mat4(" must not be the end of a longer name such as "f16mat4(".
        if (at == 0 || !(std::isalnum(static_cast<unsigned char>(body[at - 1])) || body[at - 1] == '_')) ++count;
        at += needle.size();
    }
    return count;
}

}  // namespace

long long macsPerPixel(const HookPass &pass) {
    // CuNNy and ArtCNN spell their weights M4(...) and V4(...) (mat4/vec4 or their half versions).
    long long matrices = countOf(pass.body, "mat4(") + countOf(pass.body, "M4(");
    long long vectors = pass.compute ? countOf(pass.body, "V4(") : 0;
    return matrices * 16 + vectors * 4;
}

}  // namespace a4k
