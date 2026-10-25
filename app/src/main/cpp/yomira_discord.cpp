// Discord Rich Presence for the player, through the Discord Social SDK. The SDK talks to the
// Discord app installed on the phone (no login, no token). Every SDK call runs on one worker
// thread, which also pumps the SDK callbacks.

#include <jni.h>
#include <android/log.h>

#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

namespace {

constexpr const char *kTag = "YomiraDiscord";

// Order of the text fields sent by DiscordNative.kt.
enum Field {
    kDetails = 0,
    kDetailsUrl,
    kState,
    kStateUrl,
    kLargeImage,
    kLargeText,
    kLargeUrl,
    kSmallImage,
    kSmallText,
    kSmallUrl,
    kButton1Label,
    kButton1Url,
    kButton2Label,
    kButton2Url,
    kFieldCount,
};

// Order of the number fields sent by DiscordNative.kt.
enum Number {
    kType = 0,
    kStatusDisplay,
    kStart,
    kEnd,
    kNumberCount,
};

std::mutex queueMutex;
std::condition_variable queueSignal;
std::deque<std::function<void()>> queue;
std::thread worker;
bool running = false;

// Worker thread only.
std::shared_ptr<discordpp::Client> client;
uint64_t clientAppId = 0;

std::mutex statusMutex;
std::string status = "IDLE|";

void setStatus(const std::string &phase, const std::string &message) {
    std::lock_guard<std::mutex> lock(statusMutex);
    status = phase + "|" + message;
}

std::string readStatus() {
    std::lock_guard<std::mutex> lock(statusMutex);
    return status;
}

void runLoop() {
    for (;;) {
        std::deque<std::function<void()>> tasks;
        bool stop;
        {
            std::unique_lock<std::mutex> lock(queueMutex);
            queueSignal.wait_for(lock, std::chrono::milliseconds(50), [] { return !queue.empty() || !running; });
            tasks.swap(queue);
            stop = !running;
        }
        for (auto &task : tasks) task();
        discordpp::RunCallbacks();
        if (stop) break;
    }
    client.reset();
    clientAppId = 0;
    discordpp::RunCallbacks();
}

// Starts the worker when it is not running. Call without holding queueMutex.
void startWorker() {
    std::thread previous;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        if (running) return;
        previous = std::move(worker);
    }
    if (previous.joinable()) previous.join();
    std::lock_guard<std::mutex> lock(queueMutex);
    if (running) return;
    running = true;
    worker = std::thread(runLoop);
}

void post(std::function<void()> task) {
    std::lock_guard<std::mutex> lock(queueMutex);
    if (!running) return;
    queue.push_back(std::move(task));
    queueSignal.notify_one();
}

std::string text(JNIEnv *env, jobjectArray array, jsize index) {
    if (array == nullptr || index >= env->GetArrayLength(array)) return {};
    auto value = static_cast<jstring>(env->GetObjectArrayElement(array, index));
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars != nullptr ? chars : "";
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    env->DeleteLocalRef(value);
    return result;
}

discordpp::ActivityTypes activityType(jlong value) {
    switch (value) {
        case 0: return discordpp::ActivityTypes::Playing;
        case 2: return discordpp::ActivityTypes::Listening;
        case 5: return discordpp::ActivityTypes::Competing;
        default: return discordpp::ActivityTypes::Watching;
    }
}

void ensureClient(uint64_t appId) {
    if (client && clientAppId == appId) return;
    if (!client) {
        client = std::make_shared<discordpp::Client>();
        client->AddLogCallback(
            [](std::string message, discordpp::LoggingSeverity severity) {
                if (severity == discordpp::LoggingSeverity::Error) {
                    __android_log_print(ANDROID_LOG_WARN, kTag, "%s", message.c_str());
                }
            },
            discordpp::LoggingSeverity::Error);
    }
    client->SetApplicationId(appId);
    clientAppId = appId;
    setStatus("READY", "");
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_yomira_discord_DiscordNative_nativeStart(JNIEnv *, jobject, jlong appId) {
    if (appId <= 0) return JNI_FALSE;
    startWorker();
    const auto id = static_cast<uint64_t>(appId);
    post([id] { ensureClient(id); });
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_yomira_discord_DiscordNative_nativeUpdate(JNIEnv *env, jobject, jobjectArray fields, jlongArray numbers) {
    std::vector<std::string> values(kFieldCount);
    for (jsize i = 0; i < kFieldCount; ++i) values[i] = text(env, fields, i);
    jlong nums[kNumberCount] = {3, -1, 0, 0};
    if (numbers != nullptr && env->GetArrayLength(numbers) >= kNumberCount) {
        env->GetLongArrayRegion(numbers, 0, kNumberCount, nums);
    }
    const jlong type = nums[kType];
    const jlong display = nums[kStatusDisplay];
    const jlong start = nums[kStart];
    const jlong end = nums[kEnd];

    post([values = std::move(values), type, display, start, end] {
        if (!client) {
            setStatus("ERROR", "not started");
            return;
        }
        discordpp::Activity activity;
        activity.SetType(activityType(type));
        activity.SetSupportedPlatforms(discordpp::ActivityGamePlatforms::Android);
        switch (display) {
            case 0: activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::Name); break;
            case 1: activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::State); break;
            case 2: activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::Details); break;
            default: break;
        }
        if (!values[kDetails].empty()) activity.SetDetails(values[kDetails]);
        if (!values[kDetailsUrl].empty()) activity.SetDetailsUrl(values[kDetailsUrl]);
        if (!values[kState].empty()) activity.SetState(values[kState]);
        if (!values[kStateUrl].empty()) activity.SetStateUrl(values[kStateUrl]);

        bool hasAssets = false;
        discordpp::ActivityAssets assets;
        if (!values[kLargeImage].empty()) { assets.SetLargeImage(values[kLargeImage]); hasAssets = true; }
        if (!values[kLargeText].empty()) { assets.SetLargeText(values[kLargeText]); hasAssets = true; }
        if (!values[kLargeUrl].empty()) { assets.SetLargeUrl(values[kLargeUrl]); hasAssets = true; }
        if (!values[kSmallImage].empty()) { assets.SetSmallImage(values[kSmallImage]); hasAssets = true; }
        if (!values[kSmallText].empty()) { assets.SetSmallText(values[kSmallText]); hasAssets = true; }
        if (!values[kSmallUrl].empty()) { assets.SetSmallUrl(values[kSmallUrl]); hasAssets = true; }
        if (hasAssets) activity.SetAssets(assets);

        if (start > 0) {
            discordpp::ActivityTimestamps timestamps;
            timestamps.SetStart(static_cast<uint64_t>(start));
            if (end > start) timestamps.SetEnd(static_cast<uint64_t>(end));
            activity.SetTimestamps(timestamps);
        }

        const std::pair<int, int> buttons[] = {{kButton1Label, kButton1Url}, {kButton2Label, kButton2Url}};
        for (const auto &button : buttons) {
            if (values[button.first].empty() || values[button.second].empty()) continue;
            discordpp::ActivityButton item;
            item.SetLabel(values[button.first]);
            item.SetUrl(values[button.second]);
            activity.AddButton(item);
        }

        client->UpdateRichPresence(std::move(activity), [](const discordpp::ClientResult &result) {
            if (result.Successful()) {
                setStatus("ACTIVE", "");
            } else {
                setStatus("ERROR", result.Error());
            }
        });
    });
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_yomira_discord_DiscordNative_nativeClear(JNIEnv *, jobject) {
    post([] {
        if (!client) return;
        client->ClearRichPresence();
        setStatus("CLEARED", "");
    });
}

extern "C" JNIEXPORT void JNICALL
Java_yomira_discord_DiscordNative_nativeStop(JNIEnv *, jobject) {
    std::thread stopping;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        if (!running) return;
        running = false;
        queueSignal.notify_one();
        stopping = std::move(worker);
    }
    if (stopping.joinable()) stopping.join();
    setStatus("IDLE", "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_yomira_discord_DiscordNative_nativeStatus(JNIEnv *env, jobject) {
    return env->NewStringUTF(readStatus().c_str());
}
