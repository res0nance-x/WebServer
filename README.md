# Web Server
A lightweight, modern web server built in Kotlin with directory browsing and media streaming capabilities.

- Browse files and folders
- Sort by name, size, or modification date
- Play audio, video and view images
- Audio and video files are seekable allowing skipping backwards and forwards.
- Auto play of files allows automatically playing audio, video and viewing images in sequence without intervention.
	-	Audio Playback - Built-in HTML5 audio player for MP3, FLAC, AAC, OGG, WAV, and WMA files
	-	Video Playback - Embedded video player for MP4, WebM, and OGV formats
	-	Image Preview - Direct display of images (JPG, PNG, GIF, WebP, SVG, BMP)
- View HTML as source, or cntrl-click to open as HTML.

## Keyboard Shortcuts & Interactions
### Mouse Interactions
- Ctrl+Click (or Cmd+Click on Mac) - Opens HTML files rendered as HTML in browser instead of downloading
- Regular Click - Opens/navigates to files and folders normally

### Keyboard Navigation
- Left Arrow (←) - Navigate to parent directory (go up one level)
- Right Arrow (→) - Navigate to next directory in history (if available, like browser forward)
- Backspace - Alternative way to go to parent directory

### Search/Filter
- Start Typing - Automatically filters the file list in real-time. Files and folders are filtered as you type in the search box. Filter is case-insensitive
- Sort Controls - Click column headers or sort buttons to reorder files
- Responsive Layout - Touch-friendly on mobile devices

# Usage
- Serve current directory
	- webserver
- Serve specific directory
	- webserver /path/to/directory

No external dependencies for core functionality
Responsive HTML5 interface with vanilla JavaScript
