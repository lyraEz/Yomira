cmake_minimum_required(VERSION 3.22.1)
project(yomira_discord LANGUAGES CXX)

# Rich Presence through the Discord Social SDK (app/libs/discord_partner_sdk.aar, exposed by prefab).
add_library(yomira_discord SHARED yomira_discord.cpp)

find_library(log-lib log)
find_package(discord_partner_sdk REQUIRED CONFIG)

target_compile_options(yomira_discord PRIVATE -Wall -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(yomira_discord PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
target_link_libraries(yomira_discord discord_partner_sdk::discord_partner_sdk ${log-lib})
