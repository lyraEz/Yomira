#include "hook_pipeline.h"

#include <cmath>
#include <map>
#include <set>
#include <sstream>

namespace a4k {

namespace {

const char *kVertexShader = R"(#version 300 es
out vec2 v_pos;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    v_pos = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)";

// Final copy to the output size. Downscaling averages four bilinear taps, enough for the 1x-2x
// ratios Anime4K leaves (AutoDownscalePre handles larger ones).
const char *kBlitShader = R"(#version 300 es
precision highp float;
in vec2 v_pos;
layout(location = 0) out vec4 out_color;
uniform sampler2D a4k_src;
uniform vec2 a4k_scale;
void main() {
    vec4 color;
    if (a4k_scale.x > 0.0) {
        color = 0.25 * (texture(a4k_src, v_pos + vec2(-a4k_scale.x, -a4k_scale.y))
            + texture(a4k_src, v_pos + vec2(a4k_scale.x, -a4k_scale.y))
            + texture(a4k_src, v_pos + vec2(-a4k_scale.x, a4k_scale.y))
            + texture(a4k_src, v_pos + vec2(a4k_scale.x, a4k_scale.y)));
    } else {
        color = texture(a4k_src, v_pos);
    }
    out_color = clamp(color, 0.0, 1.0);
}
)";

GLuint compileShader(GLenum type, const std::string &source, std::string *error) {
    GLuint shader = glCreateShader(type);
    const char *text = source.c_str();
    glShaderSource(shader, 1, &text, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        GLint length = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
        std::string log(length > 0 ? length : 1, '\0');
        glGetShaderInfoLog(shader, length, nullptr, &log[0]);
        *error = "shader compile failed: " + log;
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

void defineTexture(std::ostringstream &out, const std::string &name, int unit) {
    std::string u = std::to_string(unit);
    out << "#define " << name << "_raw a4k_tex" << u << "\n"
        << "#define " << name << "_pos v_pos\n"
        << "#define " << name << "_size a4k_size" << u << "\n"
        << "#define " << name << "_pt (vec2(1.0) / a4k_size" << u << ")\n"
        << "#define " << name << "_mul 1.0\n"
        << "#define " << name << "_rot mat2(1.0, 0.0, 0.0, 1.0)\n"
        << "#define " << name << "_off vec2(0.0)\n"
        << "#define " << name << "_tex(pos) (texture(a4k_tex" << u << ", (pos)))\n"
        << "#define " << name << "_texOff(off) (texture(a4k_tex" << u << ", v_pos + (vec2(1.0) / a4k_size" << u
        << ") * vec2(off)))\n";
}

}  // namespace

HookPipeline::~HookPipeline() { release(); }

bool HookPipeline::addShader(const std::string &source, std::string *error) {
    return parseHookShader(source, &passes_, error);
}

long long HookPipeline::macsPerFrame() const {
    long long total = 0;
    for (const auto &info : info_) total += info.macs;
    return total;
}

bool HookPipeline::configure(int nativeWidth, int nativeHeight, int outputWidth, int outputHeight, std::string *error) {
    release();
    nativeWidth_ = nativeWidth;
    nativeHeight_ = nativeHeight;
    outputWidth_ = outputWidth;
    outputHeight_ = outputHeight;

    // Version 0 is the input frame: it is both NATIVE and the first MAIN.
    versions_.push_back(Version{nativeWidth, nativeHeight, -1, -1});
    std::map<std::string, int> named = {{"MAIN", 0}, {"NATIVE", 0}};

    const char *stages[] = {"MAIN", "PREKERNEL"};
    for (const char *stageName : stages) {
        std::string stage = stageName;
        for (size_t p = 0; p < passes_.size(); ++p) {
            const HookPass &pass = passes_[p];
            if (pass.hook != stage) continue;
            int hooked = named["MAIN"];
            SizeLookup lookup = [&](const std::string &name, float *w, float *h) {
                int version = -1;
                if (name == "OUTPUT") {
                    *w = static_cast<float>(outputWidth);
                    *h = static_cast<float>(outputHeight);
                    return true;
                }
                if (name == "HOOKED" || name == stage) {
                    version = hooked;
                } else {
                    auto found = named.find(name);
                    if (found == named.end()) return false;
                    version = found->second;
                }
                *w = static_cast<float>(versions_[version].width);
                *h = static_cast<float>(versions_[version].height);
                return true;
            };
            if (!pass.when.empty()) {
                float condition = 0;
                if (!evalExpression(pass.when, lookup, &condition, error)) return false;
                if (condition == 0.0f) continue;
            }
            float w = static_cast<float>(versions_[hooked].width);
            float h = static_cast<float>(versions_[hooked].height);
            if (!pass.width.empty() && !evalExpression(pass.width, lookup, &w, error)) return false;
            if (!pass.height.empty() && !evalExpression(pass.height, lookup, &h, error)) return false;

            Planned planned;
            planned.pass = static_cast<int>(p);
            planned.width = static_cast<int>(std::lround(w));
            planned.height = static_cast<int>(std::lround(h));
            if (planned.width < 1 || planned.height < 1) {
                *error = "bad pass size in " + pass.desc;
                return false;
            }
            for (const auto &bind : pass.binds) {
                int version;
                if (bind == "HOOKED" || bind == stage) {
                    version = hooked;
                } else {
                    auto found = named.find(bind);
                    if (found == named.end()) {
                        *error = "texture \"" + bind + "\" is not available in " + pass.desc;
                        return false;
                    }
                    version = found->second;
                }
                planned.inputs.push_back(version);
                versions_[version].lastUse = static_cast<int>(planned_.size());
            }
            planned.output = static_cast<int>(versions_.size());
            versions_.push_back(Version{planned.width, planned.height, -1, -1});

            std::string target = pass.save.empty() ? stage : pass.save;
            if (target == stage || target == "HOOKED") {
                named["MAIN"] = planned.output;
            } else {
                named[target] = planned.output;
            }
            planned_.push_back(planned);
            info_.push_back(PlannedPassInfo{
                pass.desc,
                planned.width,
                planned.height,
                macsPerPixel(pass) * planned.width * planned.height,
            });
        }
    }
    finalVersion_ = named["MAIN"];
    versions_[finalVersion_].lastUse = static_cast<int>(planned_.size());

    // Textures are shared between passes whose results are no longer needed.
    std::vector<bool> busy;
    for (size_t i = 0; i < planned_.size(); ++i) {
        Version &out = versions_[planned_[i].output];
        int chosen = -1;
        for (size_t t = 0; t < textures_.size(); ++t) {
            if (!busy[t] && textures_[t].width == out.width && textures_[t].height == out.height) {
                chosen = static_cast<int>(t);
                break;
            }
        }
        if (chosen < 0) {
            Texture texture;
            texture.width = out.width;
            texture.height = out.height;
            glGenTextures(1, &texture.texture);
            glBindTexture(GL_TEXTURE_2D, texture.texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, out.width, out.height, 0, GL_RGBA, GL_HALF_FLOAT, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glGenFramebuffers(1, &texture.framebuffer);
            glBindFramebuffer(GL_FRAMEBUFFER, texture.framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.texture, 0);
            GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            textures_.push_back(texture);
            busy.push_back(false);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                *error = "this GPU cannot render to half-float textures";
                return false;
            }
            chosen = static_cast<int>(textures_.size() - 1);
        }
        out.physical = chosen;
        busy[chosen] = true;
        // Free what this pass was the last to read (and its own result when nothing reads it).
        for (int input : planned_[i].inputs) {
            const Version &v = versions_[input];
            if (v.physical >= 0 && v.lastUse == static_cast<int>(i)) busy[v.physical] = false;
        }
        if (out.lastUse < 0) busy[chosen] = false;
    }

    vertexShader_ = compileShader(GL_VERTEX_SHADER, kVertexShader, error);
    if (!vertexShader_) return false;
    for (auto &planned : planned_) {
        planned.program = compile(planned, error);
        if (!planned.program) return false;
    }
    blitProgram_ = link(kBlitShader, error);
    if (!blitProgram_) return false;
    glUseProgram(blitProgram_);
    glUniform1i(glGetUniformLocation(blitProgram_, "a4k_src"), 0);
    blitScaleLocation_ = glGetUniformLocation(blitProgram_, "a4k_scale");
    glUseProgram(0);
    glGenVertexArrays(1, &vao_);
    return true;
}

GLuint HookPipeline::link(const std::string &fragment, std::string *error) {
    GLuint shader = compileShader(GL_FRAGMENT_SHADER, fragment, error);
    if (!shader) return 0;
    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader_);
    glAttachShader(program, shader);
    glLinkProgram(program);
    glDeleteShader(shader);
    GLint ok = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &ok);
    if (!ok) {
        GLint length = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
        std::string log(length > 0 ? length : 1, '\0');
        glGetProgramInfoLog(program, length, nullptr, &log[0]);
        *error = "program link failed: " + log;
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

GLuint HookPipeline::compile(const Planned &planned, std::string *error) {
    const HookPass &pass = passes_[planned.pass];
    std::ostringstream out;
    out << "#version 300 es\n"
        << "precision highp float;\n"
        << "precision highp int;\n"
        << "precision highp sampler2D;\n"
        << "in vec2 v_pos;\n"
        << "layout(location = 0) out vec4 out_color;\n";
    for (size_t i = 0; i < planned.inputs.size(); ++i) {
        out << "uniform sampler2D a4k_tex" << i << ";\n"
            << "uniform vec2 a4k_size" << i << ";\n";
    }
    // Binding HOOKED also exposes the hooked texture under its own name, and the other way round.
    std::set<std::string> defined;
    for (size_t i = 0; i < pass.binds.size(); ++i) {
        std::vector<std::string> names = {pass.binds[i]};
        if (pass.binds[i] == "HOOKED") names.push_back(pass.hook);
        if (pass.binds[i] == pass.hook) names.push_back("HOOKED");
        for (const auto &name : names) {
            if (defined.insert(name).second) defineTexture(out, name, static_cast<int>(i));
        }
    }
    out << "#line 1\n" << pass.body << "\nvoid main() {\n    out_color = hook();\n}\n";
    GLuint program = link(out.str(), error);
    if (!program) {
        *error = pass.desc + ": " + *error;
        return 0;
    }
    glUseProgram(program);
    for (size_t i = 0; i < planned.inputs.size(); ++i) {
        const Version &v = versions_[planned.inputs[i]];
        std::string unit = std::to_string(i);
        glUniform1i(glGetUniformLocation(program, ("a4k_tex" + unit).c_str()), static_cast<GLint>(i));
        glUniform2f(glGetUniformLocation(program, ("a4k_size" + unit).c_str()),
                    static_cast<float>(v.width), static_cast<float>(v.height));
    }
    glUseProgram(0);
    return program;
}

bool HookPipeline::process(GLuint inputTexture, GLuint targetFramebuffer, std::string *error) {
    if (!blitProgram_) {
        *error = "pipeline not configured";
        return false;
    }
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_CULL_FACE);
    glBindVertexArray(vao_);

    glBindTexture(GL_TEXTURE_2D, inputTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    auto textureOf = [&](int version) {
        int physical = versions_[version].physical;
        return physical < 0 ? inputTexture : textures_[physical].texture;
    };

    for (const auto &planned : planned_) {
        const Texture &target = textures_[versions_[planned.output].physical];
        glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer);
        glViewport(0, 0, planned.width, planned.height);
        glUseProgram(planned.program);
        for (size_t i = 0; i < planned.inputs.size(); ++i) {
            glActiveTexture(GL_TEXTURE0 + static_cast<GLenum>(i));
            glBindTexture(GL_TEXTURE_2D, textureOf(planned.inputs[i]));
        }
        glDrawArrays(GL_TRIANGLES, 0, 3);
        if (finishEachPass) glFinish();
    }

    const Version &result = versions_[finalVersion_];
    glBindFramebuffer(GL_FRAMEBUFFER, targetFramebuffer);
    glViewport(0, 0, outputWidth_, outputHeight_);
    glUseProgram(blitProgram_);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureOf(finalVersion_));
    bool downscale = result.width > outputWidth_ || result.height > outputHeight_;
    if (downscale) {
        glUniform2f(blitScaleLocation_, 0.25f / static_cast<float>(outputWidth_), 0.25f / static_cast<float>(outputHeight_));
    } else {
        glUniform2f(blitScaleLocation_, 0.0f, 0.0f);
    }
    glDrawArrays(GL_TRIANGLES, 0, 3);

    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
    glBindVertexArray(0);
    GLenum glError = glGetError();
    if (glError != GL_NO_ERROR) {
        *error = "GL error " + std::to_string(glError);
        return false;
    }
    return true;
}

void HookPipeline::release() {
    for (auto &planned : planned_) {
        if (planned.program) glDeleteProgram(planned.program);
    }
    for (auto &texture : textures_) {
        if (texture.framebuffer) glDeleteFramebuffers(1, &texture.framebuffer);
        if (texture.texture) glDeleteTextures(1, &texture.texture);
    }
    if (blitProgram_) glDeleteProgram(blitProgram_);
    if (vertexShader_) glDeleteShader(vertexShader_);
    if (vao_) glDeleteVertexArrays(1, &vao_);
    planned_.clear();
    textures_.clear();
    versions_.clear();
    info_.clear();
    blitProgram_ = 0;
    vertexShader_ = 0;
    vao_ = 0;
}

}  // namespace a4k
