cmake_minimum_required(VERSION 3.22.1)
project(pitchandmetronome)

# Oboe — Google's low-latency audio library for Android (wraps AAudio / OpenSL ES)
# Add via Gradle's prefab or include as a subdirectory.
# See: https://github.com/google/oboe
find_package(oboe REQUIRED CONFIG)

add_library(
    pitchandmetronome
    SHARED
    # Metronome engine — AAudio output via Oboe
    metronome/MetronomeEngine.cpp
    # Pitch detector — AAudio input + YIN algorithm
    tuner/PitchDetectorEngine.cpp
    tuner/YinAlgorithm.cpp
)

target_link_libraries(
    pitchandmetronome
    oboe::oboe
    log
    android
)
