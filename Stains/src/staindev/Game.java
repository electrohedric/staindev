package staindev;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import entities.Entity;
import entities.Player;
import entities.Stain;
import gl.Shader;
import gl.Texture;
import gl.VertexArray;
import objects.Rect;
import util.Animation;
import util.ClickListener;
import util.Log;
import util.Mouse;

public class Game {

	// The window handle
	public static long window;
	private static String TITLE = "Stain Game";
	public static int WIDTH = 800;
	public static int HEIGHT = 600;
	public static VertexArray vao; // VAO for the entire Game
	public static float delta = 0.0f;
	public static List<Entity> entities;
	public static Player player;
	
	public static List<ClickListener> mouseClickCallback;
	public static List<Animation> animationQueue;
	
	public static void main(String[] args) {
		System.out.println("LWJGL version " + Version.getVersion());

		init();
		loop();

		// Free the window callbacks and destroy the window
		glfwFreeCallbacks(window);
		glfwDestroyWindow(window);

		// Terminate GLFW and free the error callback
		glfwTerminate();
		glfwSetErrorCallback(null).free();
	}

	private static void init() {
		// Setup an error callback. The default implementation
		// will print the error message in System.err.
		GLFWErrorCallback.createPrint(System.err).set();

		// Initialize GLFW. Most GLFW functions will not work before doing this.
		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW");

		// Configure GLFW
		glfwDefaultWindowHints(); // optional, the current window hints are already the default
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

		// Create the window
		window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, 0, 0);
		if (window == 0)
			throw new RuntimeException("Failed to create the GLFW window");

		// Setup a key callback. It will be called every time a key is pressed, repeated
		// or released. This will be for events such as things that happen once, other
		// keys will be recognized with glfwGetKey
		glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
			if(key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
				glfwSetWindowShouldClose(window, true);
			}
		});
		glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
			if(action == GLFW_PRESS)
				for(ClickListener listener : mouseClickCallback)
					listener.handleClick(button);
			else if(action == GLFW_RELEASE)
				for(ClickListener listener : mouseClickCallback)
					listener.handleRelease(button);
		});

		// Get the thread stack and push a new frame
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

			// Center the window
			glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
		} // the stack frame is popped automatically

		// Make the OpenGL context current
		glfwMakeContextCurrent(window);
		// Disable v-sync
		glfwSwapInterval(0);

		// Make the window visible
		glfwShowWindow(window);
		
		// init buffers and things
		
		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		// LWJGL detects the context that is current in the current thread,
		// creates the GLCapabilities instance and makes the OpenGL
		// bindings available for use.
		GL.createCapabilities();
		System.out.println("OpenGL version " + glGetString(GL_VERSION));
		
		vao = new VertexArray();
		Rect.init(); // using rectangle, so let's initialize it
		entities = new ArrayList<>();
		mouseClickCallback = new ArrayList<>();
		animationQueue = new ArrayList<>();
	}
	
	public static void checkError() {
		int error = glGetError();
		boolean errorOccured = false;
		while(error != 0) {
			System.out.println(error);
			error = glGetError();
			errorOccured = true;
		}
		if(errorOccured) {
			System.out.println("-----");
		}
	}
	
	public static void clearError() {
		while(glGetError() != 0) {} // just loop until error is 0
	}

	private static void loop() {
		// Set the clear color
		glClearColor(0.2f, 0.2f, 0.2f, 0.0f);

		/*
		 * +---+
		 * |   |
		 * +---+
		 * x, y, u, v
		 */
		
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		Shader shader = new Shader("texture.shader", "u_Texture", "u_MVP");
		
		Texture playerTexture = new Texture("player/alive.png", 98, 107, 1);
		Texture ketchupTexture = new Texture("stains/ketchup/alive.png", 33, 25, 1);
		Animation ketchupAnimation = new Animation("stains/ketchup/frame<4>.png", 24, 1, 33, 25, 1);
		
		player = new Player(500, 500, 1.0f, playerTexture, shader);
		new Stain(100, 100, 1.0f, ketchupAnimation, shader);

		// Run the rendering loop until the user has attempted to close
		// the window or has pressed the ESCAPE key.
		long lastSystemTime = System.nanoTime();
		
		while(!glfwWindowShouldClose(window)) {
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer
			
			// UPDATE & RENDER
			
			for(int i = animationQueue.size() - 1; i >= 0; i--) // have to use regular old loop to avoid ConcurrentModificationException
				animationQueue.get(i).update();
			
			for(Entity e : entities) {
				e.update();
				e.render();
			}

			glfwSwapBuffers(window); // swap the color buffers (tick)

			// Poll for window events. The key callback above will only be
			// invoked during this call.
			glfwPollEvents();
			Mouse.getUpdate();
			checkError();
			
			long currentSystemTime = System.nanoTime();
			delta = (currentSystemTime - lastSystemTime) / 1000000000.0f; // nanoseconds -> seconds
			if(delta > 1.0f) delta = 0; // if delta is way too large (e.g. Game was paused) don't process a million frames, please
			lastSystemTime = currentSystemTime;
		}

		shader.delete();
	}
}