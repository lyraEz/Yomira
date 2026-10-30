// Runs mpv user shaders (Anime4K, CuNNy) on OpenGL ES 3: the same hook order, texture names, size
// expressions and WHEN conditions as mpv, so the official shader files are used unchanged.
//
// Call every method on the thread that owns the GL context.

#pragma once

#include <GLES3/gl31.h>

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

// What happens on the GPU queue after each pass.
enum class PassSync {
    // Nothing: the driver batches as it likes.
    None = 0,
    // glFlush: every pass is sent on its own, so one frame never piles up into a single long job
    // (GPU watchdogs) while the CPU keeps going.
    Flush = 1,
    // glFinish: waits for every pass, for per-pass timings.
    Finish = 2,
};

class HookPipeline {
public:
    HookPipeline() = default;
    ~HookPipeline();
    HookPipeline(const HookPipeline &) = delete;
    HookPipeline &operator=(const HookPipeline &) = delete;

    // Adds the passes of one shader file, in the order the files are given (like mpv's list).
    bool addShader(const std::string &source, std::string *error);

    // Works out which passes run for this video and output size (WHEN conditions, sizes) and what
    // they cost, without touching GL: enough for plan() and macsPerFrame().
    bool plan(int nativeWidth, int nativeHeight, int outputWidth, int outputHeight, std::string *error);

    // plan(), then compiles the passes and allocates the textures. Needs a current GL context
    // (OpenGL ES 3.1 for compute passes).
    bool configure(int nativeWidth, int nativeHeight, int outputWidth, int outputHeight, std::string *error);

    // Processes one frame: inputTexture is an RGBA GL_TEXTURE_2D of the native size. The result is
    // drawn into targetFramebuffer (0 = default) at the output size.
    bool process(GLuint inputTexture, GLuint targetFramebuffer, std::string *error);

    void release();

    const std::vector<PlannedPassInfo> &plan() const { return info_; }
    long long macsPerFrame() const;

    PassSync sync = PassSync::Flush;

    // Fragment passes compute in half precision (texture coordinates stay in full precision): about
    // twice the arithmetic on mobile GPUs, with results that differ only in rounding. Set before
    // configure().
    bool halfPrecision = true;

private:
    // Passes the engine adds itself around the user's LUMA hooks.
    enum class Builtin { None, LumaExtract, LumaMerge };

    struct Version {
        int width = 0;
        int height = 0;
        int physical = -1;  // index into textures_, -1 = the input texture
        int lastUse = -1;
    };
    struct Planned {
        int pass = -1;  // index into passes_, -1 for a builtin pass
        Builtin builtin = Builtin::None;
        int width = 0;
        int height = 0;
        int output = 0;  // version written
        std::vector<int> inputs;  // version per BIND
        GLuint program = 0;
        int groupsX = 0;  // compute passes: work groups to dispatch
        int groupsY = 0;
    };
    struct Texture {
        GLuint texture = 0;
        GLuint framebuffer = 0;
        int width = 0;
        int height = 0;
    };

    GLuint compile(const Planned &planned, std::string *error);
    GLuint compileBuiltin(const Planned &planned, std::string *error);
    GLuint link(const std::string &fragment, std::string *error);
    GLuint linkCompute(const std::string &source, std::string *error);
    void setTextureUniforms(GLuint program, const Planned &planned);

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
