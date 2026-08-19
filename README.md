# Ultrabar Integrated - Plugin SDK

This directory contains a Netty-based Java SDK for the Ultrabar plugin protocol.

Features:
- Connect to main app (default 127.0.0.1:39001)
- Register plugin, send actions list, describe, call
- Callbacks for register/actions and actions_update
- Uses Jackson for JSON and Netty for TCP IO

Usage:
1. Build: ./gradlew jar
2. Integrate PluginClient into your application and implement callbacks.

See src/main/java/com/example/plugin for examples and model classes.
