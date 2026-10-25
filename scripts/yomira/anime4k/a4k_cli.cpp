// Runs the Yomira Anime4K engine on raw RGBA frames with a headless EGL context (Mesa works),
// so its output can be compared with libplacebo/mpv and timed.
//
//   a4k_cli --in in.rgba --size 320x180 --out out.rgba --out-size 640x360 \
//           --shaders a.glsl:b.glsl [--frames 5] [--plan]

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "hook_pipeline.h"

namespace {

bool readFile(const std::string &path, std::string *data) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return false;
    std::ostringstream buffer;
    buffer << file.rdbuf();
    *data = buffer.str();
    return true;
}

bool parseSize(const char *text, int *w, int *h) { return std::sscanf(text, "%dx%d", w, h) == 2; }

bool makeContext() {
    auto getPlatformDisplay = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYEXTPROC>(
        eglGetProcAddress("eglGetPlatformDisplayEXT"));
    EGLDisplay display = getPlatformDisplay
        ? getPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, nullptr)
        : eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) return false;
    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint configAttribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_NONE};
    EGLConfig config;
    EGLint count = 0;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &count) || count == 0) {
        const EGLint fallback[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE};
        if (!eglChooseConfig(display, fallback, &config, 1, &count) || count == 0) return false;
    }
    const EGLint contextAttribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 0, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) return false;
    return eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context);
}

}  // namespace

int main(int argc, char **argv) {
    std::string inPath, outPath, shaderList;
    int w = 0, h = 0, outW = 0, outH = 0, frames = 1;
    bool planOnly = false;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        auto next = [&]() { return i + 1 < argc ? argv[++i] : ""; };
        if (arg == "--in") inPath = next();
        else if (arg == "--out") outPath = next();
        else if (arg == "--size") parseSize(next(), &w, &h);
        else if (arg == "--out-size") parseSize(next(), &outW, &outH);
        else if (arg == "--shaders") shaderList = next();
        else if (arg == "--frames") frames = std::atoi(next());
        else if (arg == "--plan") planOnly = true;
    }
    if (w <= 0 || h <= 0 || shaderList.empty()) {
        std::cerr << "usage: a4k_cli --size WxH [--out-size WxH] --shaders a.glsl:b.glsl [--in f --out f] [--frames N] [--plan]\n";
        return 2;
    }
    if (outW <= 0) { outW = w; outH = h; }
    if (!makeContext()) {
        std::cerr << "no EGL/GLES3 context\n";
        return 1;
    }

    a4k::HookPipeline pipeline;
    std::string error;
    std::stringstream shaders(shaderList);
    std::string path;
    while (std::getline(shaders, path, ':')) {
        std::string source;
        if (!readFile(path, &source)) {
            std::cerr << "cannot read " << path << "\n";
            return 1;
        }
        if (!pipeline.addShader(source, &error)) {
            std::cerr << path << ": " << error << "\n";
            return 1;
        }
    }
    if (!pipeline.configure(w, h, outW, outH, &error)) {
        std::cerr << "configure: " << error << "\n";
        return 1;
    }
    long long macs = pipeline.macsPerFrame();
    std::cout << "passes " << pipeline.plan().size() << ", " << (macs * 2) / 1e9 << " GFLOP per frame\n";
    if (planOnly) {
        for (const auto &info : pipeline.plan()) {
            std::cout << "  " << info.width << "x" << info.height << "  " << (info.macs * 2) / 1e6 << " MFLOP  " << info.desc << "\n";
        }
        return 0;
    }

    std::vector<unsigned char> pixels(static_cast<size_t>(w) * h * 4, 128);
    if (!inPath.empty()) {
        std::string data;
        if (!readFile(inPath, &data) || data.size() != pixels.size()) {
            std::cerr << "input must be raw RGBA of " << w << "x" << h << "\n";
            return 1;
        }
        std::memcpy(pixels.data(), data.data(), pixels.size());
    }
    GLuint input;
    glGenTextures(1, &input);
    glBindTexture(GL_TEXTURE_2D, input);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());

    GLuint outTexture, outFbo;
    glGenTextures(1, &outTexture);
    glBindTexture(GL_TEXTURE_2D, outTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, outW, outH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glGenFramebuffers(1, &outFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, outTexture, 0);

    pipeline.finishEachPass = true;
    double best = 1e30, total = 0;
    for (int f = 0; f < frames; ++f) {
        auto start = std::chrono::steady_clock::now();
        if (!pipeline.process(input, outFbo, &error)) {
            std::cerr << "process: " << error << "\n";
            return 1;
        }
        glFinish();
        double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
        best = seconds < best ? seconds : best;
        total += seconds;
    }
    std::cout << "frame time best " << best * 1000 << " ms, average " << total / frames * 1000 << " ms\n";

    if (!outPath.empty()) {
        std::vector<unsigned char> out(static_cast<size_t>(outW) * outH * 4);
        glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
        glReadPixels(0, 0, outW, outH, GL_RGBA, GL_UNSIGNED_BYTE, out.data());
        std::ofstream file(outPath, std::ios::binary);
        file.write(reinterpret_cast<const char *>(out.data()), static_cast<std::streamsize>(out.size()));
    }
    return 0;
}
