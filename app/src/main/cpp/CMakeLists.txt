cmake_minimum_required(VERSION 3.22.1)

project("ytdl_bridge")

# YTDLP execution wrapper for SDK 29+ bypass
# This works on all architectures
add_executable(ytdl_wrapper ytdl_wrapper.c)
set_target_properties(ytdl_wrapper PROPERTIES OUTPUT_NAME "ytdl")
# Rename to libytdl.so so it gets extracted by Android
set_target_properties(ytdl_wrapper PROPERTIES PREFIX "lib" SUFFIX ".so")
