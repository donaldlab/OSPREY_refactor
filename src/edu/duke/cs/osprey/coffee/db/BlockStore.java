package edu.duke.cs.osprey.coffee.db;

import jdk.incubator.foreign.MemorySegment;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;


/**
 * A fixed-size storage space divided into blocks, with a very simple memory allocator
 */
public class BlockStore implements AutoCloseable {

	private static final int DefaultBlockShift = 14; // makes a 16 KiB block

	public final File file;
	public final long bytes;
	public final int blockShift;
	public final int blockSize;

	public final long numBlocks;

	private final MemorySegment mem;
	private final Deque<Long> freeBlockids = new ArrayDeque<>();

	private long nextBlockid = 0;

	public BlockStore(File file, long bytes) {
		this(file, bytes, DefaultBlockShift);
	}

	public BlockStore(File file, long bytes, int blockShift) {

		this.file = file;
		this.bytes = bytes;
		this.blockShift = blockShift;

		// how many blocks can we have?
		blockSize = 1 << blockShift;
		numBlocks = bytes/blockSize;

		// allocate the memory, either in memory, or in storage
		if (file != null) {
			try {
				if (!file.exists()) {
					file.createNewFile();
				}
				mem = MemorySegment.mapFromPath(file.toPath(), bytes, FileChannel.MapMode.READ_WRITE);
			} catch (IOException ex) {
				throw new RuntimeException("can't map path: " + file, ex);
			}
		} else {
			mem = MemorySegment.allocateNative(bytes);
		}
	}

	@Override
	public void close() {
		mem.close();
	}

	/**
	 * If there's space, allocates a block and returns its block id.
	 * Otherwise, returns -1
	 */
	public long allocateBlock() {

		Long blockid = freeBlockids.poll();
		if (blockid != null) {
			return blockid;
		}

		// allocate a block if there's space
		if (nextBlockid < numBlocks) {
			return nextBlockid++;
		}

		// out of space
		return -1;
	}

	/**
	 * Frees the block described by the given block id so someone else can use it.
	 */
	public void freeBlock(long blockid) {
		freeBlockids.offer(blockid);
	}

	public ByteBuffer get(long blockid) {
		return mem.asSlice(blockid << blockShift, blockSize).asByteBuffer();
	}
}
