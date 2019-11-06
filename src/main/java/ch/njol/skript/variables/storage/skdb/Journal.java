package ch.njol.skript.variables.storage.skdb;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.variables.VariablePath;

/**
 * SkDb's change journal. All changes are immediately written to a
 * memory-mapped file, and metadata of them is kept in memory to allow
 * rewriting the database file fast. In-memory metadata can be re-created
 * from the journal file contents, so a crash or a power loss is not likely
 * to cause (much) data loss.
 */
public class Journal {
	
	/**
	 * Serializer we should use.
	 */
	private final DatabaseSerializer serializer;
	
	/**
	 * Current journal buffer.
	 */
	private final MappedByteBuffer journalBuf;
	
	private static class ChangedVariable {
		
		/**
		 * Start of serialized data in journal buffer.
		 */
		public final int position;
		
		/**
		 * Size of serialized data.
		 */
		public final int length;
		
		public ChangedVariable(int position, int length) {
			this.position = position;
			this.length = length;
		}
	}
	
	private static class VariableTree {
		
		/**
		 * If there are other variables under this, they are stored here.
		 */
		public final Map<Object, VariableTree> contents;
		
		/**
		 * Changed variable. Null if this exists only because some variables
		 * under this were changed.
		 */
		@Nullable
		public ChangedVariable value;
		
		public VariableTree() {
			this.contents = new HashMap<>();
		}
	}
	
	/**
	 * A tree of changed variables.
	 */
	private final VariableTree root;
	
	public Journal(DatabaseSerializer serializer, Path file, int size) throws IOException {
		this.serializer = serializer;
		this.journalBuf = FileChannel.open(file).map(MapMode.READ_WRITE, 0, size);
		this.root = new VariableTree();
	}
	
	public void variableChanged(VariablePath path, @Nullable Object newValue) {
		// Write change to journal as early as possible
		serializer.writePath(path, journalBuf);
		int start = journalBuf.position();
		// TODO variable content
		int size = journalBuf.position() - start;
		
		// Find or create this variable in change tree
		VariableTree var = root;
		for (Object name : path) {
			var = var.contents.computeIfAbsent(name, (k) -> new VariableTree());
		}
		
		// (Over)write in-memory representation of data
		var.value = new ChangedVariable(start, size);
	}
}
