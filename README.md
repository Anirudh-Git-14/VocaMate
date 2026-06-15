# VocaMate – AI Voice Speaking Practice App

VocaMate is an Android voice-first AI speaking practice app built to help users improve speaking confidence, grammar, fluency, and real-life conversation skills.

The app allows users to choose a language, select a conversation mode, speak naturally, get voice replies from an AI assistant, and generate a simple mistake report after the conversation.

## Features

Voice Conversation

VocaMate uses the phone microphone to listen to the user’s speech and converts it into text using Android SpeechRecognizer.

The assistant replies based on the selected language and selected mode. The reply is also spoken aloud using Android TextToSpeech.

Supported Languages

* English
* Hindi
* Telugu
* Tamil
* Kannada
* French
* Spanish
* German
* Malayalam
* Japanese

Practice Modes

* Friend Mode
* HR Interview Mode
* Officer / Manager Mode
* Parent Mode
* Partner Mode

AI Mistake Report

After a conversation, the app generates a mistake report with:

* Original sentence
* Better sentence
* Pronunciation help
* Major mistakes
* Minor mistakes
* Grammar score
* Fluency score
* Simple advice

Mistake explanations are given in simple English so learners can understand the feedback clearly.

Practice History

Users can view:

* Full practice history
* Language-specific history
* Full transcript
* Full AI report
* Mode used
* Date and report status

## Screenshots

Splash Screen

![Splash Screen](screenshots/01_splash.jpg)

Login Screen

![Login Screen](screenshots/02_login.jpg)

Language Selection

![Language Selection](screenshots/03_language.jpg)

Mode Selection

![Mode Selection](screenshots/04_mode.jpg)

Conversation Screen

![Conversation Screen](screenshots/05_conversation.jpg)

Mistake Report

![Mistake Report](screenshots/06_report.jpg)

Practice History

![Practice History](screenshots/07_history.jpg)

History Detail

![History Detail](screenshots/08_history_detail.jpg)

Settings Screen

![Settings Screen](screenshots/09_settings.jpg)

## Tech Stack

* Android Studio
* Java
* XML
* Firebase Authentication
* Cloud Firestore
* Firebase AI Logic / Gemini
* Android SpeechRecognizer
* Android TextToSpeech
* Google Sign-In

## App Flow

Splash Screen
↓
Login Screen
↓
Language Selection
↓
Mode Selection
↓
Conversation Screen
↓
Mistake Report

## Firebase Data Structure

Practice reports are stored user-wise in Cloud Firestore.

```text
users
 └── userEmail
      ├── email
      ├── userId
      ├── lastActiveAt
      └── conversation_reports
           └── sessionId
                ├── language
                ├── mode
                ├── transcript
                ├── fullConversation
                ├── aiReport
                ├── reportStatus
                ├── createdAt
                ├── userDeleted
                └── deletedAt
```

## Purpose

Many learners know basic words and sentences, but they struggle to speak confidently.

VocaMate helps users practice speaking in a realistic conversation style. Instead of only reading grammar rules, users can speak, listen, correct mistakes, and improve through repeated practice.

## Project Status

Current version includes:

* Google login
* Multi-language selection
* Mode-based AI conversation
* Voice input
* Voice output
* AI mistake report
* Firestore storage
* Practice history
* Language-specific history
* History detail screen
* App history removal option
* Settings and logout

