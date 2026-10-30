// Parser for mpv "user shader" files (the format of the official Anime4K and CuNNy .glsl shaders).
// Only the directives those shaders use are supported: DESC, HOOK, BIND, SAVE, WIDTH, HEIGHT,
// WHEN, COMPONENTS and COMPUTE. Anything else is reported as unsupported instead of being ignored.

#pragma once

#include <functional>
#include <string>
#include <vector>

namespace a4k {

struct HookPass {
    std::string desc;
    std::string hook;
    std::vector<std::string> binds;
    std::string save;
    std::string width;   // RPN expression, empty = size of the hooked texture
    std::string height;
    std::string when;    // RPN expression, empty = always
    int components = 4;
    // //!COMPUTE bw bh [tw th]: a compute shader writing bw x bh output pixels per work group
    // with tw x th threads (bw x bh when not given).
    bool compute = false;
    int blockWidth = 0;
    int blockHeight = 0;
    int threadsWidth = 0;
    int threadsHeight = 0;
    std::string body;
};

// Parses one shader file. Returns false (and fills error) on unsupported directives.
bool parseHookShader(const std::string &source, std::vector<HookPass> *passes, std::string *error);

// Looks up "NAME.w" / "NAME.h" style variables. Returns false when the name is unknown.
using SizeLookup = std::function<bool(const std::string &name, float *width, float *height)>;

// Evaluates mpv's reverse polish size expressions ("MAIN.w 2 *", "OUTPUT.w MAIN.w / 1.2 >").
bool evalExpression(const std::string &expression, const SizeLookup &lookup, float *result, std::string *error);

// Multiply-accumulates per output pixel of a fragment pass, or per thread of a compute pass (each
// mat4 * vec4 is 16, a vec4 * float 4), for estimates.
long long macsPerPixel(const HookPass &pass);

}  // namespace a4k
