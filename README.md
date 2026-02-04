# Local Web Server
A lightweight, modern web server built in Kotlin with directory browsing and media streaming capabilities. Intended to be run locally on your LAN for viewing and playing files on other computers/devices.

- Browse files and folders
- Sort by name, size, or modification date
- Play audio, video and view images
- Audio and video files are seekable allowing skipping backwards and forwards.
- Optional toggle for automatically playing audio, video and viewing images in sequence without intervention.
	-	Audio Playback - Built-in HTML5 audio player for MP3, FLAC, AAC, OGG, WAV, and WMA files
	-	Video Playback - Embedded video player for MP4, WebM, and OGV formats
	-	Image Preview - Direct display of images (JPG, PNG, GIF, WebP, SVG, BMP)
- View HTML as source, or cntrl-click to open as HTML.

## Keyboard Shortcuts & Interactions
### Mouse Interactions
- Ctrl+Click (or Cmd+Click on Mac) - Opens HTML files rendered as HTML in browser instead of downloading
- Regular Click - Opens/navigates to files and folders normally

### Keyboard Navigation
- Navigate files in the directory list with the up (↑) and down (↓) arrows and then selecting a file or subdirectory with enter.
- Exit viewing of media by pressing escape.
- Open media in a seperate tab with ctrl-enter
- Go to parent directory with backspace

### Search/Filter
- Select filter area and start Files and folders are filtered as you type in the search box. Filter is case-insensitive
- Sort Controls - Click column headers or sort buttons to reorder files
- Responsive Layout - Touch-friendly on mobile devices

# Usage
- Serve current directory
	- java -jar webserver.jar
- Serve specific directory
	- java -jar webserver.jar /path/to/directory
- Server on a specific port
    - java -jar webserver.jar -p 8080 /path/to/directory

No external dependencies for core functionality
Responsive HTML5 interface with vanilla JavaScript

For this and other projects check out https://www.res0nance.cc/index.html
