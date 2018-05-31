package guis;

import objects.GameObject;
import objects.Surface;
import staindev.Game;

public class Screen extends GameObject {

	public Screen(Surface texture, float x, float y, float scale) {
		super(x, y, 0, scale, false);
		setActiveTexture(texture);
	}
	
	public Screen(Surface texture) {
		this(texture, Game.WIDTH / 2, Game.HEIGHT / 2, 1.0f);
	}
	
}
