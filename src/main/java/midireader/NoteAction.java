package midireader;

public class NoteAction {
	public String note;
	public boolean pressed;
	public int channel;
	public long timestamp;
	
	public NoteAction(String note, boolean pressed, int channel) {
		this(note, pressed, channel, 0);
	}
	
	public NoteAction(String note, boolean pressed, int channel, long timestamp) {
		this.note = note;
		this.pressed = pressed;
		this.channel = channel;
		this.timestamp = timestamp;
	}
}
