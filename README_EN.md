# AIAnswerer

[中文指南](README.md#中文使用指南) | [English Guide](#english-guide)

## English Guide

### Overview
AIAnswerer combines on-device OCR with large language models on Android. Capture questions through a floating overlay, let DeepSeek AI (OpenAI-compatible) analyze them, and receive instant answers—ideal for practice, review, or self-testing.

> Screenshot placeholder: App home or welcome screen

### Key Features
- 🖼️ Quick capture: Snap the current screen and auto-focus on the question area
- 📝 Smart OCR: Recognizes Chinese and English text with manual editing before submission
- 🤖 Instant AI answers: Generates explanations and copies the result to the clipboard
- 💬 Floating workflow: Control capture, preview, and submit without leaving your app
- 🔒 Full control: Bring your own API Key and pause network requests whenever needed

### Getting Ready
1. Use a device running Android 11 or later and ensure a stable internet connection.
2. Install the provided APK; enable “unknown sources” when prompted during the first install.
3. Prepare your DeepSeek API Key (paste it in the in-app settings if the build ships without one; contact your distributor if no input field is available).
4. During the first launch, grant overlay, screenshot, and notification permissions as requested.

### Quick Start
1. Launch the app, tap “Enter answering mode,” and confirm that all permissions are granted.  
   > Screenshot placeholder: Permission dialogs
2. Open the screen with your question and tap the floating button to capture it.  
   > Screenshot placeholder: Floating button guide
3. Review the recognized text in the confirmation view; adjust it if anything looks off.
4. Tap “Confirm & solve” to receive the AI-generated answer, which is also copied to your clipboard.
5. To exit, return to the main screen and tap “Stop service.”

### Supported Question Types
- Multiple choice: Extracts question and options, highlights a recommended answer with reasoning
- Fill in the blank: Produces concise entries for each blank slot
- Short/long answer: Supplies structured explanations or outline-style responses

### Tips & Tricks
- Keep screenshots sharp and centered, and avoid busy backgrounds to improve OCR accuracy.
- Pause AI requests by toggling the setting in-app or briefly disconnecting from the network.
- Capture again at any time via the floating button to continue practicing new questions.

### FAQ
- **Missing permissions?** Open system settings, search for “overlay” or “screen capture,” and enable the required entries manually.
- **Incorrect recognition?** Edit the text on the confirmation screen or retake the screenshot.
- **No AI response?** Verify connectivity, confirm your API Key, and make sure your DeepSeek balance covers the request.

### Privacy & Disclaimer
- Recognized text is sent to your chosen AI provider; avoid sensitive or restricted content.
- DeepSeek usage may incur costs—monitor your API consumption responsibly.
- AIAnswerer is for learning and research purposes only. Respect exam rules and local regulations; you are accountable for any misuse.

### License
This project is released under the [MIT License](/LICENSE)