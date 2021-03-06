cmake_minimum_required(VERSION 3.5)
project(moments)

set(CMAKE_C_STANDARD 99)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_C_FLAGS "-Wall -Wextra")
set(CMAKE_C_FLAGS_DEBUG "-g")
set(CMAKE_C_FLAGS_RELEASE "-O3 -ffast-math -march=native -DHAVE_INLINE -DGSL_RANGE_CHECK_OFF")

find_package(GSL REQUIRED)

set(LIB_FILES mlib.c mlib.h util.h)
add_library(mlib SHARED ${LIB_FILES})
target_link_libraries(mlib GSL::gsl GSL::gslcblas m)

add_executable(main main.c)
target_link_libraries (main mlib)

add_executable(rtest rtest.c greatest.h)
target_link_libraries(rtest mlib)
