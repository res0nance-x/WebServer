import r3.http.*
import r3.io.consistentFile
import java.io.File

fun main(args: Array<String>) {
	// Default values
	var directory = "."
	var host: String? = null
	var port = 80
	var showHelp = false
	fun printUsage() {
		println("Usage: WebServer [options] [directory]")
		println("Options:")
		println("  --host HOST, -h HOST\tSet the host to bind (optional). If '-h' is provided without a value it will show this help.")
		println("  --port PORT, -p PORT\tSet the port to listen on (default 80)")
		println("  -?, --help, help, ?\tShow this help message")
		println()
		println("Notes:")
		println("  The first non-option argument that is not 'help' or '?' is treated as the directory to serve. Options may appear before or after the directory.")
	}
	// Parse all arguments. The first token that does NOT start with '-' is the directory, except 'help' or '?'.
	var idx = 0
	while (idx < args.size) {
		val a = args[idx]
		// Recognize explicit help tokens even if they are not prefixed with '-'
		if (!a.startsWith("-")) {
			if (a == "help" || a == "?") {
				showHelp = true
				break
			}
			// First non-option token -> directory (only set once)
			if (directory == ".") {
				directory = a
			} else {
				// If directory already set, warn and ignore extra positional args
				System.err.println("Warning: extra positional argument '$a' will be ignored")
			}
			idx += 1
			continue
		}
		// Handle long-form with '=' first: --host=..., --port=... and help flags
		when {
			a == "-?" || a == "--help" || a == "-help" -> {
				showHelp = true
			}

			a.startsWith("--host=") -> host = a.substringAfter("=")
			a == "--host" -> {
				if (idx + 1 < args.size && !args[idx + 1].startsWith("-")) {
					host = args[idx + 1]
					idx += 1
				} else {
					System.err.println("Warning: --host provided but no host value found; keeping default (null)")
				}
			}
			// '-h' is ambiguous: if followed by a non-option it's the host, otherwise treat as help
			a == "-h" -> {
				if (idx + 1 < args.size && !args[idx + 1].startsWith("-")) {
					host = args[idx + 1]
					idx += 1
				} else {
					showHelp = true
				}
			}

			a.startsWith("--port=") -> {
				port = a.substringAfter("=").toIntOrNull() ?: run {
					System.err.println("Warning: invalid port in '$a', using default $port")
					port
				}
			}

			a == "--port" -> {
				if (idx + 1 < args.size && !args[idx + 1].startsWith("-")) {
					port = args[idx + 1].toIntOrNull() ?: run {
						System.err.println("Warning: invalid port '${args[idx + 1]}', using default $port")
						port
					}
					idx += 1
				} else {
					System.err.println("Warning: --port provided but no port value found; keeping default $port")
				}
			}

			a == "-p" -> {
				if (idx + 1 < args.size && !args[idx + 1].startsWith("-")) {
					port = args[idx + 1].toIntOrNull() ?: run {
						System.err.println("Warning: invalid port '${args[idx + 1]}', using default $port")
						port
					}
					idx += 1
				} else {
					System.err.println("Warning: -p provided but no port value found; keeping default $port")
				}
			}

			else -> {
				// unknown option - print usage and exit
				System.err.println("Unrecognized option: '$a'")
				printUsage()
				return
			}
		}

		idx += 1
	}

	if (showHelp) {
		printUsage()
		return
	}
	// Normalize directory and verify it exists
	val rootDir: File = File(directory).consistentFile()
	if(!rootDir.exists() || !rootDir.isDirectory) {
		System.err.println("Error: Specified directory '$directory' does not exist or is not a directory.")
		return
	}

	println("Starting web server on port $port${if (host != null) ", binding host '$host'" else ""}")
	val dirRouter = DirectoryListingRouter(
		rootDir = rootDir
	)
	val server = WebServer(
		host,
		port = port,
		defaultWebSocketBuilder,
		listOf(
			RequestLogRouter,
			FileRouter(
				rootDir = rootDir,
				host = null
			),
			FavIconRouter(),
			dirRouter
		)
	)
	server.start(0, false)
}