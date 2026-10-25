#include "hook_shader.h"

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

long long macsPerPixel(const HookPass &pass) {
    long long count = 0;
    size_t at = 0;
    while ((at = pass.body.find("mat4(", at)) != std::string::npos) {
        ++count;
        at += 5;
    }
    return count * 16;
}

}  // namespace a4k
