cmake_minimum_required(VERSION 3.20)
project(requests)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 依赖根（交叉编译产物）
set(DEPS /home/dsh/omp-server-android/plugins)
set(CPPREST_INC ${DEPS}/cpprestsdk-install/include)
set(CPPREST_LIB ${DEPS}/cpprestsdk-install/lib)
set(BOOST_INC ${DEPS}/boost_1_78_0)
set(BOOST_LIB ${DEPS}/boost_1_78_0/stage/lib)
set(OPENSSL_INC /tmp/openssl-3.0.13/include)
set(OPENSSL_LIB /tmp/openssl-3.0.13)
set(ZLIB_INC ${DEPS}/zlib-install/include)
set(ZLIB_LIB ${DEPS}/zlib-install/lib)
set(WS_INC ${DEPS}/cpprestsdk/Release/libs/websocketpp)
set(SAMPSDK ${CMAKE_CURRENT_SOURCE_DIR}/lib/samp-plugin-sdk)

add_library(requests MODULE
  ${SAMPSDK}/amxplugin.cpp
  ${SAMPSDK}/amxplugin2.cpp
  ${SAMPSDK}/amx/getch.c
  src/common.hpp
  src/requests.cpp
  src/impl.cpp
  src/impl.hpp
  src/natives.cpp
  src/natives.hpp
)

set_target_properties(requests PROPERTIES PREFIX "")
set_target_properties(requests PROPERTIES OUTPUT_NAME "requests")

target_compile_definitions(requests PRIVATE
  PAWN_CELL_SIZE=32
  LINUX
)

target_include_directories(requests PRIVATE
  ${SAMPSDK}
  ${SAMPSDK}/amx
  ${CMAKE_CURRENT_SOURCE_DIR}/src
  ${CPPREST_INC}
  ${BOOST_INC}
  ${OPENSSL_INC}
  ${ZLIB_INC}
  ${WS_INC}
)

# 链接：cpprestsdk + boost + openssl + zlib + Android log
target_link_libraries(requests
  ${CPPREST_LIB}/libcpprest.so.2.10
  ${BOOST_LIB}/libboost_system.a
  ${BOOST_LIB}/libboost_thread.a
  ${BOOST_LIB}/libboost_chrono.a
  ${BOOST_LIB}/libboost_filesystem.a
  ${BOOST_LIB}/libboost_atomic.a
  ${BOOST_LIB}/libboost_random.a
  ${BOOST_LIB}/libboost_date_time.a
  ${BOOST_LIB}/libboost_regex.a
  ${OPENSSL_LIB}/libssl.a
  ${OPENSSL_LIB}/libcrypto.a
  ${ZLIB_LIB}/libz.a
  log
)

# 编译时忽略 wrapper 的链接 flags 触发的警告（-Wno-unused-command-line-argument）
target_compile_options(requests PRIVATE -Wno-unused-command-line-argument)

# 让 requests.so 从插件目录（$ORIGIN）找 libcpprest.so
set_target_properties(requests PROPERTIES
  INSTALL_RPATH "\$ORIGIN"
  BUILD_RPATH "\$ORIGIN"
  LINK_FLAGS "-Wl,-rpath,\$ORIGIN"
)
