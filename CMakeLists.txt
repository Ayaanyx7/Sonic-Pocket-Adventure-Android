cmake_minimum_required(VERSION 3.25)

project(SonicPocket LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# NGPC machine: MAME-derived TLCS-900/H CPU, K2GE video and T6W28 sound, a Z80
# core and a high-level BIOS.

add_library(tlcs900 STATIC
    src/cpu/tlcs900/tlcs900.cpp
)
target_include_directories(tlcs900 PUBLIC src)
target_compile_features(tlcs900 PUBLIC cxx_std_20)

add_library(z80core STATIC
    third_party/z80/z80.c
)
target_include_directories(z80core PUBLIC third_party/z80)

add_library(ngpc STATIC
    src/ngpc/ngpc_cpu.cpp
    src/ngpc/k2ge.cpp
    src/ngpc/machine.cpp
    src/ngpc/t6w28.cpp
    src/common/png.cpp
)
target_include_directories(ngpc PUBLIC src)
target_link_libraries(ngpc PUBLIC tlcs900 z80core)

if(NOT ANDROID)
    # Desktop tools & recompiler (not needed when cross-compiling for Android)
    add_executable(ngpc-run src/apps/ngpc_run.cpp)
    target_link_libraries(ngpc-run PRIVATE ngpc)

    add_executable(spa-recomp
        src/recomp/main.cpp
        src/recomp/decoder.cpp
    )
    target_link_libraries(spa-recomp PRIVATE tlcs900)
endif()

if(MSVC)
    target_compile_options(tlcs900 PRIVATE /W3)
    target_compile_options(ngpc PRIVATE /W4 /permissive-)
    if(TARGET ngpc-run)
        target_compile_options(ngpc-run PRIVATE /W4 /permissive-)
    endif()
endif()

# ---------------------------------------------------------------------------
# The recompiled game.

set(SPA_ROM "" CACHE FILEPATH "Sonic Pocket Adventure cartridge image")
set(SPA_LISTING "" CACHE FILEPATH "Optional listing_instructions.txt")
set(SPA_PREGENERATED_DIR "" CACHE PATH "Path to pre-generated C++ sources (for cross-compiling libspa_generated)")
set(SPA_PREBUILT_LIB "" CACHE FILEPATH "Path to pre-compiled libspa_generated.a")

if(SPA_ROM OR SPA_PREGENERATED_DIR OR SPA_PREBUILT_LIB)

    if(SPA_PREBUILT_LIB)
        # Mode 3: Fast Android APK build using pre-compiled libspa_generated.a
        add_library(spa_generated STATIC IMPORTED)
        set_target_properties(spa_generated PROPERTIES
            IMPORTED_LOCATION "${SPA_PREBUILT_LIB}"
        )
    else()
        # Mode 1 & 2: Compile the 33 generated C++ files into libspa_generated.a
        if(SPA_PREGENERATED_DIR)
            set(SPA_GENERATED_DIR "${SPA_PREGENERATED_DIR}")
        else()
            set(SPA_GENERATED_DIR "${CMAKE_BINARY_DIR}/generated")
        endif()

        set(SPA_GENERATED_SOURCES "${SPA_GENERATED_DIR}/dispatch.cpp")
        foreach(shard RANGE 0 31)
            string(LENGTH "${shard}" shard_length)
            if(shard_length EQUAL 1)
                set(shard "0${shard}")
            endif()
            list(APPEND SPA_GENERATED_SOURCES "${SPA_GENERATED_DIR}/game_${shard}.cpp")
        endforeach()

        if(NOT SPA_PREGENERATED_DIR)
            set(SPA_RECOMP_ARGS
                --rom "${SPA_ROM}"
                --functions "${CMAKE_SOURCE_DIR}/config/recomp/functions.txt"
                --out "${SPA_GENERATED_DIR}"
            )
            if(SPA_LISTING)
                list(APPEND SPA_RECOMP_ARGS --listing "${SPA_LISTING}")
            endif()

            add_custom_command(
                OUTPUT ${SPA_GENERATED_SOURCES} "${SPA_GENERATED_DIR}/functions.h"
                COMMAND spa-recomp ${SPA_RECOMP_ARGS}
                DEPENDS spa-recomp "${CMAKE_SOURCE_DIR}/config/recomp/functions.txt" "${SPA_ROM}"
                COMMENT "Recompiling Sonic Pocket Adventure"
                VERBATIM
            )
        endif()

        add_library(spa_generated STATIC ${SPA_GENERATED_SOURCES})
        target_include_directories(spa_generated PRIVATE src "${SPA_GENERATED_DIR}")
        target_compile_features(spa_generated PRIVATE cxx_std_20)
        if(MSVC)
            target_compile_options(spa_generated PRIVATE /bigobj /EHsc)
        endif()
    endif()

    # Live runtime wrapper (can be edited freely without rebuilding spa_generated)
    add_library(spa_game STATIC
        src/recomp_rt/runtime.cpp
        src/recomp_rt/game.cpp
    )
    target_include_directories(spa_game PUBLIC src)
    target_link_libraries(spa_game PUBLIC ngpc)

    if(ANDROID)
        # --whole-archive prevents ld from stripping dynamically dispatched routines
        target_link_libraries(spa_game PUBLIC
            "-Wl,--whole-archive"
            spa_generated
            "-Wl,--no-whole-archive"
        )
    else()
        target_link_libraries(spa_game PUBLIC spa_generated)
    endif()

    # Stop here if we are only building the static library in Workflow 1
    if(NOT SPA_ONLY_LIB)
        if(NOT ANDROID)
            add_executable(spa-verify src/apps/spa_verify.cpp)
            target_link_libraries(spa-verify PRIVATE spa_game)
        endif()

        # Fetch SDL3
        include(FetchContent)
        set(SDL_SHARED ON CACHE BOOL "" FORCE)
        set(SDL_STATIC OFF CACHE BOOL "" FORCE)
        set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
        set(SDL_TESTS OFF CACHE BOOL "" FORCE)
        set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
        FetchContent_Declare(
            SDL3
            GIT_REPOSITORY https://github.com/libsdl-org/SDL.git
            GIT_TAG release-3.4.8
            GIT_SHALLOW TRUE
        )
        FetchContent_MakeAvailable(SDL3)

        if(ANDROID)
            # SDL3 on Android expects a shared library loaded by SDLActivity
            add_library(sonic-pocket SHARED src/apps/sonic_pocket.cpp)
            target_link_libraries(sonic-pocket PRIVATE spa_game SDL3::SDL3)
        else()
            add_executable(sonic-pocket src/apps/sonic_pocket.cpp)
            target_link_libraries(sonic-pocket PRIVATE spa_game SDL3::SDL3)
            add_custom_command(
                TARGET sonic-pocket
                POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    $<TARGET_FILE:SDL3::SDL3>
                    $<TARGET_FILE_DIR:sonic-pocket>
            )
        endif()
    endif()
endif()
