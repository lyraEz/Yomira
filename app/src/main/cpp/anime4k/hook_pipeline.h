// Runs mpv user shaders (Anime4K) on OpenGL ES 3: the same hook order, texture names, size
// expressions and WHEN conditions as mpv, so the official shader files are used unchanged.
//
// Call every method on the thread that owns the GL context.

#pragma once

#include <GLES3/gl3.h>

#include <string>
#include <vector>

#include "hook_shader.h"

namespace a4k {

struct PlannedPassInfo {
    std::string desc;
    int width = 0;
    int height = 0;
    long long macs = 0;  // per frame
};

class HookPipeline {
public:
    HookPipeline() = default;
    ~HookPipeline();
    HookPipeline(const HookPipeline &) = delete;
    HookPipeline &operator=(const HookPipeline &) = delete;

    // Adds the passes of one shader file, in the order the files are given (like mpv's list).
    bool addShader(const std::string &source, std::string *error);

    // Works out which passes run for this video and output size, compiles them and allocates
    // the textures. Needs a current GL context.
    bool configure(int nativeWidth, int nativeHeight, int outputWidth, int outputHeight, std::string *error);

    // Processes one frame: inputTexture is an RGBA GL_TEXTURE_2D of the native size. The result is
    // drawn into targetFramebuffer (0 = default) at the output size.
    bool process(GLuint inputTexture, GLuint targetFramebuffer, std::string *error);

    void release();

    const std::vector<PlannedPassInfo> &plan() const { return info_; }
    long long macsPerFrame() const;

    // glFinish after every pass: keeps one long frame from piling up on the GPU (watchdogs) and
    // gives real timings in benchmarks.
    bool finishEachPass = false;

private:
    struct Version {
        int width = 0;
        int height = 0;
        int physical = -1;  // index into textures_, -1 = the input texture
        int lastUse = -1;
    };
    struct Planned {
        int pass = 0;
        int width = 0;
        int height = 0;
        int output = 0;  // version written
        std::vector<int> inputs;  // version per BIND
        GLuint program = 0;
    };
    struct Texture {
        GLuint texture = 0;
        GLuint framebuffer = 0;
        int width = 0;
        int height = 0;
    };

    GLuint compile(const Planned &planned, std::string *error);
    GLuint link(const std::string &fragment, std::string *error);

    std::vector<HookPass> passes_;
    std::vector<Version> versions_;
    std::vector<Planned> planned_;
    std::vector<Texture> textures_;
    std::vector<PlannedPassInfo> info_;
    int finalVersion_ = 0;
    int nativeWidth_ = 0;
    int nativeHeight_ = 0;
    int outputWidth_ = 0;
    int outputHeight_ = 0;
    GLuint vertexShader_ = 0;
    GLuint blitProgram_ = 0;
    GLint blitScaleLocation_ = -1;
    GLuint vao_ = 0;
};

}  // namespace a4k
