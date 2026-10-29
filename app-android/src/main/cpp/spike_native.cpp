#include <jni.h>
#include <cctype>

// Fast token estimate: max(chars / 4, word count). Good enough for a context meter.
extern "C" JNIEXPORT jint JNICALL
Java_dev_spike_android_Native_estimateTokens(JNIEnv *env, jobject /*thiz*/, jstring text) {
    const char *c = env->GetStringUTFChars(text, nullptr);
    if (!c) return 0;
    size_t chars = 0, words = 0;
    bool inWord = false;
    for (const char *p = c; *p; ++p) {
        ++chars;
        if (std::isspace(static_cast<unsigned char>(*p))) inWord = false;
        else if (!inWord) { inWord = true; ++words; }
    }
    env->ReleaseStringUTFChars(text, c);
    size_t est = (chars + 3) / 4;
    return static_cast<jint>(est > words ? est : words);
}
