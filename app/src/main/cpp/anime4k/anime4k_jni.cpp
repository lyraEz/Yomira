// JNI bridge for yomira.anime4k.Anime4KNative. Every call except create/destroy needs the GL
// context that will run the passes to be current on the calling thread.

#include <jni.h>

#include <GLES3/gl31.h>

#include <memory>
#include <string>

#include "hook_pipeline.h"

namespace {

std::string text(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars != nullptr ? chars : "";
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring errorOrNull(JNIEnv *env, bool ok, const std::string &error) {
    return ok ? nullptr : env->NewStringUTF(error.c_str());
}

a4k::HookPipeline *pipelineOf(jlong handle) { return reinterpret_cast<a4k::HookPipeline *>(handle); }

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_yomira_anime4k_Anime4KNative_nativeCreate(JNIEnv *env, jobject, jobjectArray sources, jobjectArray errorOut) {
    auto pipeline = std::make_unique<a4k::HookPipeline>();
    std::string error;
    jsize count = env->GetArrayLength(sources);
    for (jsize i = 0; i < count; ++i) {
        auto source = static_cast<jstring>(env->GetObjectArrayElement(sources, i));
        bool ok = pipeline->addShader(text(env, source), &error);
        env->DeleteLocalRef(source);
        if (!ok) {
            if (errorOut != nullptr && env->GetArrayLength(errorOut) > 0) {
                env->SetObjectArrayElement(errorOut, 0, env->NewStringUTF(error.c_str()));
            }
            return 0;
        }
    }
    return reinterpret_cast<jlong>(pipeline.release());
}

extern "C" JNIEXPORT jstring JNICALL
Java_yomira_anime4k_Anime4KNative_nativeConfigure(
    JNIEnv *env, jobject, jlong handle, jint nativeWidth, jint nativeHeight, jint outputWidth, jint outputHeight) {
    std::string error;
    bool ok = pipelineOf(handle)->configure(nativeWidth, nativeHeight, outputWidth, outputHeight, &error);
    return errorOrNull(env, ok, error);
}

extern "C" JNIEXPORT jlong JNICALL
Java_yomira_anime4k_Anime4KNative_nativePlanMacs(
    JNIEnv *, jobject, jlong handle, jint nativeWidth, jint nativeHeight, jint outputWidth, jint outputHeight) {
    std::string error;
    if (!pipelineOf(handle)->plan(nativeWidth, nativeHeight, outputWidth, outputHeight, &error)) return -1;
    return static_cast<jlong>(pipelineOf(handle)->macsPerFrame());
}

extern "C" JNIEXPORT jlong JNICALL
Java_yomira_anime4k_Anime4KNative_nativeMacsPerFrame(JNIEnv *, jobject, jlong handle) {
    return static_cast<jlong>(pipelineOf(handle)->macsPerFrame());
}

extern "C" JNIEXPORT jint JNICALL
Java_yomira_anime4k_Anime4KNative_nativePassCount(JNIEnv *, jobject, jlong handle) {
    return static_cast<jint>(pipelineOf(handle)->plan().size());
}

extern "C" JNIEXPORT jstring JNICALL
Java_yomira_anime4k_Anime4KNative_nativeProcess(
    JNIEnv *env, jobject, jlong handle, jint inputTexture, jint targetFramebuffer, jint sync) {
    GLuint target = 0;
    if (targetFramebuffer < 0) {
        GLint bound = 0;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &bound);
        target = static_cast<GLuint>(bound);
    } else {
        target = static_cast<GLuint>(targetFramebuffer);
    }
    auto *pipeline = pipelineOf(handle);
    pipeline->sync = sync == 2 ? a4k::PassSync::Finish : sync == 1 ? a4k::PassSync::Flush : a4k::PassSync::None;
    std::string error;
    bool ok = pipeline->process(static_cast<GLuint>(inputTexture), target, &error);
    return errorOrNull(env, ok, error);
}

extern "C" JNIEXPORT void JNICALL
Java_yomira_anime4k_Anime4KNative_nativeReleaseGl(JNIEnv *, jobject, jlong handle) {
    pipelineOf(handle)->release();
}

extern "C" JNIEXPORT void JNICALL
Java_yomira_anime4k_Anime4KNative_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    delete pipelineOf(handle);
}
