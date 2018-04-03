package gl;

import static org.lwjgl.opengl.GL15.*;

public class IndexBuffer {

	private int id;
	
	public IndexBuffer(short[] data) {
		id = glGenBuffers();
		bind();
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, data, GL_STATIC_DRAW);
	}
	
	public void bind() {
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, id);
	}
	
	public void unbind() {
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
	}
	
	public void delete() {
		glDeleteBuffers(id);
	}
}
