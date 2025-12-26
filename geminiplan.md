This architecture plan is designed for your specific "IT Admin" context: a robust, low-maintenance, serverless backend feeding a dedicated Android client.

### **Project Codename: "CloudAmp"**

#### **1. High-Level Architecture**

The system uses a **"Thin Client, Fat Backend"** approach. The Android phone is just a remote control and audio renderer; the heavy lifting (decryption, extraction, parsing) happens on Google Cloud Run.

---

### **2. Backend Architecture (The Brain)**

**Hosting:** Google Cloud Run (Serverless Container)
**Language:** Python 3.11 (FastAPI)
**Core Dependencies:** `yt-dlp`, `ffmpeg`, `uvicorn`

* **Endpoint 1: `/search/lucky**`
* **Input:** `?q=Song Name`
* **Logic:**
1. Appends `"official audio"` to query to prioritize high-quality streams.
2. Executes `yt-dlp "ytsearch1:..." --get-url --dump-json`.
3. **Critical:** Extracts the **Opus** audio stream URL (highest quality/lowest bandwidth).
4. Fetches **SponsorBlock** segments for the video ID.


* **Output:** JSON with `stream_url`, `title`, `artist`, `thumbnail`, and `skip_segments` (arrays of start/end times).


* **Endpoint 2: `/playlist/sync` (Future Proofing)**
* **Input:** YouTube Playlist URL.
* **Logic:** Uses `yt-dlp --flat-playlist` to quickly grab video IDs without downloading files.
* **Output:** JSON array of tracks to populate your local library.


* **Container Strategy:**
* **Base Image:** `python:3.11-slim` (Debian-based for easy FFmpeg install).
* **Concurrency:** Set `concurrency: 80` (FastAPI handles async requests well).
* **Memory:** 512MiB is sufficient; 1GB recommended if doing heavy playlist parsing.



---

### **3. Frontend Architecture (The Body)**

**Device:** Pixel 5 (Stock Android 14)
**Framework:** React Native (Expo SDK 50+)
**Key Libraries:** `expo-av`, `expo-file-system`, `@react-native-async-storage/async-storage`

* **Audio Engine (`expo-av`)**
* **Mode:** `interruptionModeAndroid: InterruptionModeAndroid.DoNotMix` (Pauses for calls, ducks for others—though irrelevant for your non-GPS phone, good practice).
* **Background Play:** Must configure `app.json` with `android.permissions: ["FOREGROUND_SERVICE"]` to keep the music alive when the screen is off.
* **SponsorBlock Logic:**
* The app runs a `setInterval` check every 1 second during playback.
* *If* `currentPosition` is inside a `skip_segment` range  `soundObject.setPositionAsync(end_time)`.




* **The "Winamp" UI**
* **Main Screen:** Large "Now Playing" text, Album Art, and massive Play/Pause/Skip buttons (driver-friendly).
* **Library:** Simple list view powered by `FlashList` (better performance than FlatList).
* **Visualizer (Optional):** Use `react-native-skia` for a simple waveform if you want that 90s feel without killing the battery.



---

### **4. Data Flow: The "Lucky" Play**

1. **User:** Taps "Search" and voice-types "Hotel California".
2. **App:** Sends `GET /search/lucky?q=Hotel California` to Cloud Run.
3. **Cloud Run:**
* Spins up (2-3s cold start if inactive).
* `yt-dlp` finds the video ID `EqPtz5qN7HM`.
* Extracts direct stream URL: `https://rr3---sn-vgqsrn7s.googlevideo.com/...`
* Fetches Skip Segments: `[{start: 0, end: 15, category: 'intro'}]`.


4. **App:**
* Receives JSON.
* Loads URL into `expo-av`.
* **Auto-Skip:** Immediately seeks to `0:15` (skipping the silence/applause).
* Updates Lock Screen metadata (Title, Artist, Art).



---

### **5. Resilience & "IT Admin" Controls**

* **The "403" Defense (User-Agent Passthrough):**
* YouTube sometimes blocks a stream if the device requesting the audio (Pixel 5) has a different "User Agent" than the device that found the link (Cloud Run).
* **Plan:** Hardcode a standard specific User Agent (e.g., "Mozilla/5.0... Chrome/120...") in the backend. Send this same string to the app in the JSON response. The app *must* use this User Agent in the `expo-av` headers when requesting the stream.


* **Updates:**
* `yt-dlp` breaks often. Add a line in your `Dockerfile` to `pip install --upgrade yt-dlp` so every new deployment pulls the latest version.



---

### **6. Implementation Steps (Your Weekend Plan)**

1. **Phase 1 (Backend):** Deploy the Docker container to Cloud Run. Test it with `curl` on your laptop to confirm it returns a playable URL.
2. **Phase 2 (Hello World App):** Create a blank Expo app with one button: "Play Song". Hardcode it to hit your backend. Confirm audio plays on the Pixel 5.
3. **Phase 3 (The UI):** Build the "Winamp" skin and Search bar.
4. **Phase 4 (The Polish):** Add the SponsorBlock polling logic and Background Service config.