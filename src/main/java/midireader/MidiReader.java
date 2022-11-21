package midireader;

import binaryutil.BinaryUtil;
import binaryutil.BinaryUtil.Significance;
import binaryutil.BinaryUtil.TypeOfShift;
import filereader.FileReader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Optional;
import org.jfugue.midi.MidiFileManager;
import org.jfugue.pattern.Pattern;
import org.jfugue.player.Player;

public class MidiReader extends FileReader {
	public static void main(String[] args) {
		//File file = new File("midi/RUshEconvertedwhitemidi.mid");
		//File file = new File("midi/Rush E (Original).mid"); //paljon ylimäärästä paskaa
		//File file = new File("midi/Titantic.mid");
		//File file = new File("midi/YoureBeautiful.mid"); //somehow doesn't work
		//File file = new File("midi/HotelCalifornia.mid");
		//File file = new File("midi/PokerFace.mid");
		//File file = new File("midi/PinkPanther.mid");
		//File file = new File("midi/HarryPotter.mid"); //bad
		//File file = new File("midi/HarryPotterPrologue.mid");
		File file = new File("midi/Numb.mid");
		
		MidiReader midiReader = new MidiReader();
		midiReader.readFile(file);
		
		midiReader.printNotesPressed();
		
		Test.test(file); //this code prints the same stuff as mine using javax library
		
		//play the file:
		try {
			Player player = new Player();
			Pattern pattern = MidiFileManager.loadPatternFromMidi(file);
			System.out.println(pattern);
			player.play(pattern);
			//System.out.println(pattern);
			//player.play("C D E F G A B C6 ");
		} catch (Exception e) {
			System.out.println("Error: " + e);
		}
	}
	
	private ArrayList<NoteAction> notesPressed = new ArrayList<>();
	private long currentTime = 0;
	private short format;
	private short ticksPerQuarterNote;
	private long microsecPerQuarterNote;
	
	private String lastEventStatus = null;
	
	private void readFile(File file) {
		resetReader();
		Optional<String> ext = FileReader.getExtension(file.getPath());
		
		if (!ext.isPresent()) {
			return;
		}
		
		switch (ext.get().toLowerCase()) {
			case "mid":
				readMidi(file);
				System.out.println("Microseconds in one delta-time: " + (microsecPerQuarterNote / (double) ticksPerQuarterNote));
				break;
		}
	}
	
	private void readMidi(File file) {
		try {
			byte[] bytes = Files.readAllBytes(file.toPath());
			
			print("length: " + bytes.length);
			
			String start = readChars(bytes, readerHEAD, 4);
			print("headerText: " + start);
			
			int size = readInt(bytes, readerHEAD, ByteOrder.BIG_ENDIAN);
			print("size: " + size);
			
			format = readShort(bytes, readerHEAD, ByteOrder.BIG_ENDIAN); //0: contains only 1 track, 1: contains 1 or more tracks, played simultaneously, 2: contains one or more tracks, played independently
			print("format: " + format);
			
			short tracks = readShort(bytes, readerHEAD, ByteOrder.BIG_ENDIAN);
			print("tracks: " + tracks);
			
			short division = readShort(bytes, readerHEAD, ByteOrder.BIG_ENDIAN);
			
			int typeOfDelta = BinaryUtil.firstBit(division); //if 0, then next is ticksPerQuarterNote, if 1, next is 14-8 -frames/second and 7-0 ticks / frame
			print("typeOfDelta: " + typeOfDelta);
			
			if (typeOfDelta == 0) {
				ticksPerQuarterNote = BinaryUtil.getOnlySomeBits(division, 15, Significance.LEAST_SIGNIFICANT_BIT, TypeOfShift.NO_SHIFT);
				print("ticksPerQuarterNote: " + ticksPerQuarterNote + " (how many delta-times in one quarter note)");
			} else if (typeOfDelta == 1) {
				print("-frames/second: " + "not done yet");
			}
			
			print("");
			
			for (int i = 0; i < tracks; i++) {
				if (format != 2) {
					currentTime = 0;
				}
				readTrackChunk(bytes);
				print("");
			}
		} catch (IOException e) {
			
		}
	}
	
	private void readTrackChunk(byte[] bytes) {
		String start = readChars(bytes, readerHEAD, 4);
		print("trackText: " + start);
		
		int size = readInt(bytes, readerHEAD, ByteOrder.BIG_ENDIAN);
		print("size: " + size);
		
		while (readEvent(bytes)) {}
		
	}
	
	private boolean readEvent(byte[] bytes) {
		int deltaTime = readVariableLengthQty(bytes, readerHEAD);
		print("deltaTime: " + deltaTime);
		currentTime += deltaTime;
		
		byte val = readByte(bytes, readerHEAD);
		String type = BinaryUtil.toHexString(val, 2, false, false).toUpperCase();
		
		boolean keepGoing = true;
		
		if (Integer.parseInt(type, 16) <= 127) { //running status, status is the same as last time.
			if (lastEventStatus.charAt(0) >= '8' && lastEventStatus.charAt(0) <= 'E') { //is legal running status
				readerHEAD--;
				type = lastEventStatus;
				print("running status");
			} else {
				print("ERROR!");
				keepGoing = false;
			}
		}
		
		if (type.equals("F0")) {
			print("sysex event start: " + type);
			readSysexEvent(bytes, true);
		} else if (type.equals("F7")) {
			print("sysex event end: " + type);
			readSysexEvent(bytes, false);
		} else if (type.equals("FF")) {
			print("metaEvent: " + type);
			keepGoing = readMetaEvent(bytes);
		} else if (type.charAt(0) >= '8' && type.charAt(0) <= 'E') {
			readMidiEvent(bytes, type);
		} else {
			print("something else: " + type);
			keepGoing = false;
		}
		
		if (Integer.parseInt(type, 16) < 248) { //if >= 248 it is System Real-Time Message, and it shouldn't affect running status
			lastEventStatus = type;
		}
		
		print("");
		return keepGoing;
	}
	
	private boolean readMetaEvent(byte[] bytes) {
		byte val = readByte(bytes, readerHEAD);
		String type = BinaryUtil.toHexString(val, 2, false, false).toUpperCase();
		
		print("type: " + type);
		
		int length = readVariableLengthQty(bytes, readerHEAD);
		print("length: " + length);
		
		boolean keepGoing = true;
		
		switch (type) {
			case "00":
				print("Sequence number: " + readShort(bytes, readerHEAD, ByteOrder.BIG_ENDIAN));
				return keepGoing;
			case "01":
				break;
			case "02": //copyright notice
				String copyright = readChars(bytes, readerHEAD, length);
				print("copyright: " + copyright);
				return keepGoing;
			case "03": //Sequence/track name
				String name = readChars(bytes, readerHEAD, length);
				print("name: " + name);
				return keepGoing;
			case "04": //instrument name
				print("instrument name: " + readChars(bytes, readerHEAD, length));
				return keepGoing;
			case "05": //lyrics (maybe one character or couple)
				print("Lyric: " + readChars(bytes, readerHEAD, length));
				return keepGoing;
			case "06": //marker
				print("Marker: " + readChars(bytes, readerHEAD, length));
				return keepGoing;
			case "07":
				break;
			case "20":
				print("midi channel: " + readByte(bytes, readerHEAD));
				return keepGoing;
			case "21":
				print("midi port: " + readByte(bytes, readerHEAD));
				return keepGoing;
			case "2F": //end of track
				print("END OF TRACK!");
				return false;
			case "51": //set tempo: microsec / quarter note. 500000 * 4 * 60 / 1000000 = 120 beats/minute
				microsecPerQuarterNote = readBytesAsLong(bytes, readerHEAD, length, ByteOrder.BIG_ENDIAN);
				int tempo = (int) (microsecPerQuarterNote * 4 * 60 / 1000000);
				
				print("microsecPerQuarterNote: " + microsecPerQuarterNote);
				print("tempo: " + tempo + " BPM");
				return keepGoing;
			case "54":
				break;
			case "58": //time signature first / 2^second. third = number of midi clocks per metronome tick. fourth is number of 1/32 notes per 24 midi clocks (default: 8)
				byte numerator = readByte(bytes, readerHEAD);
				byte denominator = readByte(bytes, readerHEAD);
				byte midiClocksPerMetronomeTick = readByte(bytes, readerHEAD);
				byte lastOne = readByte(bytes, readerHEAD);
				
				print("Time signature: " + numerator + "/" + (int) Math.pow(2, denominator) + ", midiClocksPerMetronomeTick: " + midiClocksPerMetronomeTick + ", the weird one: " + lastOne);
				return keepGoing;
			case "59":
				byte sf = readByte(bytes, readerHEAD);
				byte mi = readByte(bytes, readerHEAD);
				String sfString = sf < 0 ? Math.abs(sf) + " flats" : (sf == 0 ? "C" : Math.abs(sf) + " sharps");
				String miString = mi == 0 ? "major" : "minor";
				
				print("Key signature: " + sf + " (" + sfString + ") " + miString);
				return keepGoing;
			case "7F":
				break;
		}
		
		long data = readBytesAsLong(bytes, readerHEAD, length, ByteOrder.BIG_ENDIAN);
		print("data: " + BinaryUtil.toHexString(data, length * 2, true, false));
		return keepGoing;
	}
	
	private void readSysexEvent(byte[] bytes, boolean start) {
		int length = readVariableLengthQty(bytes, readerHEAD);
		print("length: " + length);
		
		long data = readBytesAsLong(bytes, readerHEAD, length, ByteOrder.BIG_ENDIAN);
		print("data: " + BinaryUtil.toHexString(data, length * 2, true, false));
	}
	
	private void readMidiEvent(byte[] bytes, String type) {
		int channel = Integer.parseInt("" + type.charAt(1), 16);
		
		print("midi event: " + type + " channel: " + channel);
		
		byte first = readByte(bytes, readerHEAD);
		
		switch (type.charAt(0)) {
			case '8': //note off
				String note = convertToNote(first);
				notesPressed.add(new NoteAction(note, false, channel, currentTime));
				print("note off: " + note + " (key: " + BinaryUtil.toHexString(first, 2, true, false) + " velocity: " + readHexByte(bytes, readerHEAD, true) + ")");
				break;
			case '9': //note on
				note = convertToNote(first);
				byte velocity = readByte(bytes, readerHEAD); //if velocity is 0, it is considered to be note off instead to help running statuses
				boolean flipped = velocity == 0;
				
				NoteAction noteAction = new NoteAction(note, !flipped, channel, currentTime);
				velocity = (byte) (flipped ? 0x40 : velocity);
				notesPressed.add(noteAction);
				
				print("note " + (flipped ? "off" : "on") + ": " + note + " (key: " + BinaryUtil.toHexString(first, 2, true, false) + " velocity: " + BinaryUtil.toHexString(velocity, 2, true, false) + ")");
				break;
			case 'A':
				print("aftertouch: (key: " + BinaryUtil.toHexString(first, 2, true, false) + " pressure: " + readHexByte(bytes, readerHEAD, true) + ")");
				break;
			case 'B':
				print("controller number: " + BinaryUtil.toHexString(first, 2, true, false));
				print("controller value: " + readByte(bytes, readerHEAD));
				break;
			case 'C':
				print("program change: (new program number: " + BinaryUtil.toHexString(first, 2, true, false) + ")");
				break;
			case 'D':
				print("aftertouch to whole keyboard: (pressure: " + BinaryUtil.toHexString(first, 2, true, false) + ")");
				break;
			case 'E':
				print("pitch bender lever: (lsB: " + BinaryUtil.toHexString(first, 2, true, false) + " msB: " + readHexByte(bytes, readerHEAD, true) + ")");
				break;
		}
	}
	
	public static String convertToNote(int i) {
		int octave = i / 12 - 1;
		int note = i % 12;
		
		String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
		
		return notes[note] + octave;
	}
	
	public void printNotesPressed() {
		print("NOTES PRESSED:");
		
		for (NoteAction note : notesPressed) {
			if (note.pressed) {
				print(note.note + "\tchannel: " + note.channel + "\tTimeStamp: " + note.timestamp);
			}
		}
	}
}
