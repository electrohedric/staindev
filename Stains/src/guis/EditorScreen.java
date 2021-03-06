package guis;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import constants.Mode;
import constants.Resources;
import constants.Sounds;
import constants.StainType;
import constants.Textures;
import gl.FrameBufferRenderBuffer;
import gl.Renderer;
import guis.elements.Button;
import guis.elements.RadioButton;
import guis.elements.RadioButtonChannel;
import objects.GameObject;
import objects.Map;
import objects.Spawner;
import staindev.Game;
import util.Camera;
import util.ClickListener;
import util.Cursors;
import util.Key;
import util.Log;
import util.Mouse;
import util.Music;

/** Singleton which represents the only Editor Screen */
public class EditorScreen extends Gui implements ClickListener {
	
	private static EditorScreen instance;
	
	private Segment ghostWall;
	private Dot ghostDot;
	private Arc ghostArc;
	private Map map;
	private Segment[] gridLines;
	private GameObject spawnPoint;
	private List<Button> placeMenuButtons;
	private PlaceType placeType;
	private StainType placeSpawnerType;
	
	private Tool tool;
	private Tool lastTool;
	private boolean firstPointDown;
	private int firstClickX;
	private int firstClickY;
	private boolean filletFirstSelection;
	private boolean filletSelectingRadius;
	private GameObject placing;
	private boolean scrollingScreen;
	private int lastMouseX;
	private int lastMouseY;
	private long lastCursor;
	protected Camera camera;
	
	private boolean showingPlaceMenu;
	private Plane placeMenuBackground;
	
	private Segment intersecting;
	
	private int GRID_SIZE, GRID_WIDTH, GRID_HEIGHT, GRID_MAX_X, GRID_MAX_Y;
	protected float WALL_WIDTH;
	
	private EditorScreen() {
		super(Textures.Editor.BG);
		RadioButtonChannel toolsChannel = new RadioButtonChannel();
		
 		elements.add(new RadioButton(toolsChannel, Game.WIDTH * 0.92f, Game.HEIGHT * 0.8f, 0.1f, Textures.Editor.LINE, Mode.EDITOR, true, () -> {
 			firstPointDown = false;
			tool = Tool.LINE;
			setMousePointer(Cursors.POINTER);
		}));
		elements.add(new RadioButton(toolsChannel, Game.WIDTH * 0.92f, Game.HEIGHT * 0.6f, 0.1f, Textures.Editor.FILLET, Mode.EDITOR, true, () -> {
			tool = Tool.FILLET;
			setMousePointer(Cursors.HAND);
		}));
		elements.add(new RadioButton(toolsChannel, Game.WIDTH * 0.92f, Game.HEIGHT * 0.4f, 0.1f, Textures.Editor.REMOVE, Mode.EDITOR, true, () -> {
			tool = Tool.REMOVE;
			setMousePointer(Cursors.CROSS);
		}));
		this.tool = Tool.SELECT;
		this.lastTool = tool;
		
		elements.add(new Button(Game.WIDTH * 0.92f, Game.HEIGHT * 0.2f, 0.1f, Textures.Editor.PLACE, Mode.EDITOR, true, () -> {
			lastTool = tool;
			tool = Tool.PLACE;
			lastCursor = getMousePointer();
			setMousePointer(Cursors.POINTER);
			showingPlaceMenu = true;
			for(Button b : placeMenuButtons)
				b.enable();
			for(Button b : elements)
				b.disable();
		}));
		elements.add(new Button(Game.WIDTH * 0.97f, Game.HEIGHT * 0.95f, 0.08f, Textures.Editor.SAVE, Mode.EDITOR, true, () -> {
			map.saveMap();
			if(Key.down(GLFW.GLFW_KEY_LEFT_SHIFT) || Key.down(GLFW.GLFW_KEY_RIGHT_SHIFT))
				saveMapImage();
		}));
		elements.add(new Button(Game.WIDTH * 0.90f, Game.HEIGHT * 0.95f, 0.08f, Textures.Editor.LOAD, Mode.EDITOR, true, () -> {
			map = Map.loadMap();
		}));
		
		this.camera = new Camera(0, 0);
		
		this.WALL_WIDTH = 3.0f;
		this.ghostWall = new Segment(0, 0, 0, 0, WALL_WIDTH, 127, 127, 127, 255); // instantiate a wall with just a gray color
		this.ghostDot = new Dot(0, 0, WALL_WIDTH * 3, 127, 127, 127, 255);
		this.ghostArc = new Arc(null, null, 0, WALL_WIDTH, 60, 60, 60, 255);
		this.map = new Map(); // create a completely empty map
		this.placeMenuButtons = new ArrayList<>();
		this.placeType = PlaceType.NONE;
		this.placeSpawnerType = StainType.KETCHUP; // arbitrary default, not really important
		resetClicks();
		
		float menuWidth = Game.WIDTH * 0.75f;
		float menuHeight = Game.HEIGHT * 0.75f;
		float menuDimX = 5;
		float borderSize = 20;
		float buttonSize = (menuWidth - borderSize * (menuDimX + 1)) / menuDimX;
		this.showingPlaceMenu = false;
		this.placeMenuBackground = new Plane(Game.WIDTH / 2, Game.HEIGHT / 2, menuWidth, menuHeight, 50, 50, 50, 200); // mostly opaque and dark gray
		this.spawnPoint = new GameObject(Game.WIDTH / 2, Game.HEIGHT / 2, 0, 0.02f);
		spawnPoint.setActiveTexture(Textures.Editor.PLAYER_SPAWN);
		this.placing = new GameObject(0, 0, 0, 0); // placing any object in the popup menu
		placing.brightScale = 0.4f; // make bright when placing
		float menuLeft = Game.WIDTH / 2 - menuWidth / 2 + borderSize + buttonSize / 2;
		//float menuRight = Game.WIDTH / 2 + menuWidth / 2 - borderSize - buttonSize / 2;
		float menuTop = Game.HEIGHT / 2 + menuHeight / 2 - borderSize - buttonSize / 2;
		//float menuBottom = Game.HEIGHT / 2 - menuHeight / 2 + borderSize + buttonSize / 2;
		
		// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
		// add menu buttons here with x=0,y=0 ... they will get organized right after
		placeMenuButtons.add(new Button(0, 0, 0.05f, Textures.KETCHUP_ALIVE, Mode.EDITOR, true, () -> {
			placing.scale = 0.03f;
			placing.setActiveTexture(Textures.KETCHUP_ALIVE);
			placeType = PlaceType.SPAWNER;
			placeSpawnerType = StainType.KETCHUP;
			showingPlaceMenu = false;
		}));
		placeMenuButtons.add(new Button(0, 0, 0.05f, Textures.Editor.PLAYER_SPAWN, Mode.EDITOR, true, () -> {
			placing.scale = 0.02f;
			placing.setActiveTexture(Textures.Editor.PLAYER_SPAWN);
			placeType = PlaceType.SPAWNPOINT;
			showingPlaceMenu = false;
		}));
		
		// menu buttons get organized
		float buttonGap = buttonSize + borderSize;
		int _c = 0, _r = 0;
		for(Button b : placeMenuButtons) {
			b.x = menuLeft + _c * buttonGap;
			b.y = menuTop - _r * buttonGap;
			b.disable();
			if(++_c == menuDimX) {
				_r++;
				_c = 0;
			}
		}
		
		this.GRID_SIZE = 35;
		this.GRID_WIDTH = 100;
		this.GRID_HEIGHT = 60;
		this.gridLines = new Segment[GRID_WIDTH + GRID_HEIGHT];
		this.GRID_MAX_X = (GRID_WIDTH - 1) * GRID_SIZE;
		this.GRID_MAX_Y = (GRID_HEIGHT - 1) * GRID_SIZE;
		
		float gridline_width = 1.0f;
		for(int c = 0; c < GRID_WIDTH; c++)
			gridLines[c] = new Segment(c * GRID_SIZE, 0, c * GRID_SIZE, GRID_MAX_Y, gridline_width, 0, 0, 50, 255);
		for(int r = 0; r < GRID_HEIGHT; r++)
			gridLines[GRID_WIDTH + r] = new Segment(0, r * GRID_SIZE, GRID_MAX_X, r * GRID_SIZE, gridline_width, 0, 0, 50, 255);
		
		ClickListener.addToCallback(this, Mode.EDITOR);
		instance = this;
	}

	private void resetClicks() {
		firstPointDown = false;
		firstClickX = 0;
		firstClickY = 0;
		filletFirstSelection = false;
		filletSelectingRadius = false;
	}
	
	public static EditorScreen getInstance() {
		if(instance != null)
			return instance;
		else
			return new EditorScreen();
	}
	
	private int mouseGridX() {
		return grid(camera.getMouseX(), GRID_SIZE);
	}
	
	private int mouseGridY() {
		return grid(camera.getMouseY(), GRID_SIZE);
	}
	
	/**
	 * Attempts to select a segment and colors it according to r,g,b if found. Sets <code>interesting</code> if found
	 */
	private void trySelect(int r, int g, int b) {
		if(intersecting != null)
			intersecting.resetColor();
		float bestAccuracy = 1.0f; // 1.0f == worst possible
		for(Segment wall : map.walls) {
			float accuracy = wall.intersectsPoint(camera.getMouseX(), camera.getMouseY());
			if(accuracy < bestAccuracy) {
				bestAccuracy = accuracy;
				intersecting = wall;
			}
		}
		if(bestAccuracy < 0.005f)  // if accuracy better than 0.5%, we've got an intersection
			intersecting.setColor(r, g, b, 255); // reset prior intersecting wall's color
		else
			intersecting = null;
	}
	
	@Override
	public void update() {
		super.update();
		switch(tool) {
		case FILLET:
			if(filletSelectingRadius) {
				Vector2f corner = ghostArc.getTangent1().findCorner(ghostArc.getTangent2());
				Vector2f cursorVec = new Vector2f(mouseGridX() - corner.x, mouseGridY() - corner.y);
				float dist = cursorVec.length();
				if(dist != ghostArc.getDistance()) { // we don't want to force an update if we don't have to
					ghostArc.setDistance(dist);
				}
				
			} else {
				trySelect(50, 50, 200);  // blueish
			}
			break;
		case LINE:
			if(firstPointDown) {
				ghostWall.setEndPoint(mouseGridX(), mouseGridY());
			} else {
				ghostDot.setPos(mouseGridX(), mouseGridY());
			}
			break;
		case REMOVE:
			trySelect(200, 50, 50);  // red-ish
			break;
		case SELECT:
			trySelect(0, 0, 0);
			break;
		case PLACE:
			for(Button b : placeMenuButtons)
				b.update();
			if(!showingPlaceMenu) { // must be placing
				placing.x = camera.getMouseX();
				placing.y = camera.getMouseY();
			}
			// TODO add limitations to coords
			break;
		}
		if(scrollingScreen) {
			int dx = Mouse.x - lastMouseX;
			int dy = Mouse.y - lastMouseY;
			camera.x -= dx;
			camera.y -= dy;
			lastMouseX = Mouse.x;
			lastMouseY = Mouse.y;
		}
	}
	
	public void render() {
		super.renderBackground();
		// XXX grid possibly more effecient rendering?
		for(Segment gridLine : gridLines)
			gridLine.render(camera);
		super.renderElements();
		for(Arc arc : map.fillets)
			arc.render(camera);
		if(tool == Tool.FILLET) { // render extra ghost arc for the fillet tool
			if(filletSelectingRadius) {
				ghostArc.render(camera);
			}
		}
		for(Segment wall : map.walls)
			wall.render(camera);
		if(tool == Tool.LINE) { // we only need extra ghost wall rendering for the line tool
			if(isOnMap()) {
				if(firstPointDown)
					ghostWall.render(camera);
				else
					ghostDot.render(camera);
			}
		}
		spawnPoint.render(camera);
		for(GameObject spawner : map.spawners)
			spawner.render(camera);
		if(tool == Tool.PLACE) {
			if(showingPlaceMenu) {
				placeMenuBackground.render();
				for(Button b : placeMenuButtons)
					b.render();
			} else { // placing
				placing.render(camera);
			}
		}
	}
	
	private int grid(int value, int interval) {
		return (int) Math.floor(((value + interval / 2.0) / interval)) * interval; // floor so -0.5 will round to -1 not 0
	}
	
	@Override
	public void handleClick(int button) { // handle painting
		if(isOnMap()) {
			switch(tool) {
			case FILLET:
				if(button == Mouse.LEFT) {
					if(filletSelectingRadius) {
						map.fillets.add(new Arc(ghostArc.getTangent1(), ghostArc.getTangent2(), ghostArc.getDistance(), WALL_WIDTH, 250, 250, 250, 255));
						filletFirstSelection = false;
						filletSelectingRadius = false;
					} else if(intersecting != null) {
						if(filletFirstSelection) {
							ghostArc.setTangent2(intersecting);
							filletFirstSelection = false;
							filletSelectingRadius = true;
						} else {
							ghostArc.setTangent1(intersecting);
							filletFirstSelection = true;
						}
					}
				} else if(button == Mouse.RIGHT){
					filletFirstSelection = false;
					filletSelectingRadius = false;
				}
				break;
			case LINE:
				boolean onButton = false;
				for(Button b : elements) {
					if(b.isMouseHovering()) {
						onButton = true;
						break;
					}
					
				}
				if(button == Mouse.LEFT && !onButton) {
					int clickX = mouseGridX();
					int clickY = mouseGridY();
					if(!(clickX == firstClickX && clickY == firstClickY && firstPointDown)) { // don't do anything if starting point and ending point are the same
						if(firstPointDown)
							map.walls.add(new Segment(firstClickX, firstClickY, clickX, clickY, WALL_WIDTH, 250, 250, 250, 255)); // FIXME no duplicate walls and no intersections. color red if these
						firstClickX = clickX; // start next wall off where this one ended
						firstClickY = clickY;
						ghostWall.setStartPoint(firstClickX, firstClickY);
						firstPointDown = true;
					}
				} else if(button == Mouse.RIGHT) {
					firstPointDown = false;
				}
				break;
			case REMOVE:
				if(button == Mouse.LEFT) {
					if(intersecting == null) {
						// TODO bounding box
					} else {
						map.walls.remove(intersecting);
					}
				}
				break;
			case SELECT:
				
				break;
			case PLACE:
				if(button == Mouse.LEFT) {
					if(!showingPlaceMenu) { // second click will place the item
						switch(placeType) {
						case SPAWNER:
							Spawner toAdd = new Spawner(placeSpawnerType, (int) placing.x, (int) placing.y);
							toAdd.setActiveTexture(placing.getActiveTexture());
							map.spawners.add(toAdd);
						case NONE:
							break;
						case SPAWNPOINT:
							map.initialSpawnX = camera.getMouseX();
							map.initialSpawnY = camera.getMouseY();
							spawnPoint.x = map.initialSpawnX;
							spawnPoint.y = map.initialSpawnY;
						default: break;
						}
						placeType = PlaceType.NONE; // every item after placed will show place menu again
						showingPlaceMenu = true; // TODO system for continuous placement if control held or something
					}
				} else if(button == Mouse.RIGHT) { // right click gets rid of everything
					showingPlaceMenu = false;
					for(Button b : placeMenuButtons)
						b.disable();
					for(Button b : elements)
						b.enable();
					tool = lastTool;
					setMousePointer(lastCursor);
				}
				break;
			}
			if(button == Mouse.MIDDLE) {
				scrollingScreen = true;
				lastMouseX = Mouse.x;
				lastMouseY = Mouse.y;
				lastCursor = getMousePointer();
				setMousePointer(Cursors.HAND);
			}
			// XXX allow zooming for convenience
		}
	}
	
	@Override
	public void handleRelease(int button) {
		if(button == Mouse.MIDDLE) {
			setMousePointer(lastCursor);
			scrollingScreen = false;
		}
	}
	
	private boolean isOnMap() {
		int gx = mouseGridX();
		int gy = mouseGridY();
		return gx >= 0 && gx <= GRID_MAX_X && gy >= 0 && gy <= GRID_MAX_Y;
	}
	
	
	private void saveMapImage() {
		float minX = spawnPoint.x;
		float maxX = minX;
		float minY = spawnPoint.y;
		float maxY = minY;
		for(Segment seg : map.walls) {
			minX = Math.min(minX, Math.min(seg.getX1(), seg.getX2()));
			maxX = Math.max(maxX, Math.max(seg.getX1(), seg.getX2()));
			minY = Math.min(minY, Math.min(seg.getY1(), seg.getY2()));
			maxY = Math.max(maxY, Math.max(seg.getY1(), seg.getY2()));
		}
		int bufferX = 40;
		int bufferY = 30;
		int width = (int) (maxX - minX) + bufferX * 2;
		int height = (int) (maxY - minY) + 1 + bufferY * 2;
		
		Camera fboCam = new Camera(minX - bufferX, minY - bufferY);
		FrameBufferRenderBuffer fbo = new FrameBufferRenderBuffer(width, height);
		fbo.activate();// render to the renderbuffer instead of the default fbo for displaying
		Renderer.setClearColor(0, 0, 0);
		glClear(GL_COLOR_BUFFER_BIT); // set background for temp fbo
		
		// render just the parts we want
		float renderWidth = 1.2f;
		for(Arc arc : map.fillets) {
			arc.setWidth(renderWidth);
			arc.render(fboCam);
			arc.setWidth(WALL_WIDTH);
		}
		for(Segment wall : map.walls) {
			wall.setWidth(renderWidth);
			wall.render(fboCam);
			wall.setWidth(WALL_WIDTH);
		}
		
		spawnPoint.scale = 0.01f;
		spawnPoint.render(fboCam);
		spawnPoint.scale = 0.02f;
		
		// save the fbo to a file
		BufferedImage img = fbo.readPixels();
		
		String name = "mostRecentMap.png";
		try {
		    ImageIO.write(img, "png", new File(Resources.MAPS_PATH + name));
		} catch (IOException e) {
			Log.err("Cannot write to file: " + Resources.MAPS_PATH + name);
			e.printStackTrace();
		}
		fbo.deactivate(); // restore
		fbo.delete(); // dont need it no mo'
		Log.log("Saved map capture as " + name);
		Sounds.SCARY.forcePlay();
	}
	
	@Override
	public void switchTo() {
		Game.mode = Mode.EDITOR;
		Music.transition(1.0f, () -> { // run this after music has faded out in 1 second
			Music.queueLoop(Sounds.EDITOR_LOOP); // XXX new music loop plz
			Music.play();
		});
	}

	
	private static enum Tool {
		SELECT, LINE, FILLET, REMOVE, PLACE;
	}
	
	private static enum PlaceType {
		NONE, SPAWNPOINT, SPAWNER
	}
	
}
