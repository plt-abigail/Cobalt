/*
 * ****************************************************************************
 * Original Source: Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 * Modifications: Copyright (c) 2015-2017 SquidDev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ****************************************************************************
 */

package org.squiddev.cobalt.debug;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.function.LuaFunction;

import java.lang.ref.WeakReference;

/**
 * DebugState is associated with a Thread
 */
public final class DebugState {
	public static final int MAX_SIZE = 256;

	public static final int DEFAULT_SIZE = 8;

	private static final DebugFrame[] EMPTY = new DebugFrame[0];

	/**
	 * The thread that owns this object
	 */
	private final WeakReference<LuaThread> thread;

	/**
	 * The thread's lua state
	 */
	private final LuaState state;

	private final DebugHandler handler;

	/**
	 * The top function
	 */
	public int top = -1;

	/**
	 * The stack of debug info
	 */
	private DebugFrame[] stack = EMPTY;

	/**
	 * The hook function to call
	 */
	public DebugHook hookfunc;

	/**
	 * Which item hooks should be called on
	 */
	public boolean hookcall, hookline, hookrtrn, inhook;

	/**
	 * Number of instructions to execute
	 */
	public int hookcount;

	/**
	 * Number of instructions executed
	 */
	public int hookcodes;

	public DebugState(LuaThread thread) {
		this.thread = new WeakReference<LuaThread>(thread);
		this.state = thread.luaState;
		this.handler = state.debug;
	}

	/**
	 * Push a new debug info onto the stack
	 *
	 * @return The created info
	 * @throws LuaError On a stack overflow.
	 */
	protected DebugFrame pushInfo() throws LuaError {
		int top = this.top + 1;

		DebugFrame[] frames = stack;
		int length = frames.length;
		if (top >= length) {
			if (top >= MAX_SIZE) throw new LuaError("stack overflow");

			int newSize = length == 0 ? DEFAULT_SIZE : length + (length / 2);
			DebugFrame[] f = new DebugFrame[newSize];
			System.arraycopy(frames, 0, f, 0, frames.length);
			for (int i = frames.length; i < newSize; ++i) {
				f[i] = new DebugFrame(i > 0 ? f[i - 1] : null);
			}
			frames = stack = f;
		}

		this.top = top;
		return frames[top];
	}

	/**
	 * Pop a debug info off the stack
	 */
	protected void popInfo() {
		stack[top--].clear();
	}

	/**
	 * Setup the hook
	 *
	 * @param func  The function hook
	 * @param call  Hook on calls
	 * @param line  Hook on lines
	 * @param rtrn  Hook on returns
	 * @param count Number of bytecode operators to use
	 */
	public void setHook(DebugHook func, boolean call, boolean line, boolean rtrn, int count) {
		this.hookcount = count;
		this.hookcall = call;
		this.hookline = line;
		this.hookrtrn = rtrn;
		this.hookfunc = func;
	}

	/**
	 * Copy hooks from another debug state
	 *
	 * @param other The state to copy from
	 */
	public void setHook(DebugState other) {
		this.hookcount = other.hookcount;
		this.hookcall = other.hookcall;
		this.hookline = other.hookline;
		this.hookrtrn = other.hookrtrn;
		this.hookfunc = other.hookfunc;
	}

	/**
	 * Get the top debug info
	 *
	 * @return The top debug info or {@code null}
	 */
	public DebugFrame getStack() {
		return top >= 0 ? stack[top] : null;
	}

	/**
	 * Get the debug info at a particular level
	 *
	 * @param level The level to get at
	 * @return The debug info or {@code null}
	 */
	public DebugFrame getDebugInfo(int level) {
		return level >= 0 && level <= top ? stack[top - level] : null;
	}

	/**
	 * Find the debug info for a function
	 *
	 * @param func The function to find
	 * @return The debug info for this function
	 */
	public DebugFrame findDebugInfo(LuaFunction func) {
		for (int i = top - 1; --i >= 0; ) {
			if (stack[i].func == func) {
				return stack[i];
			}
		}
		return new DebugFrame(func);
	}

	@Override
	public String toString() {
		LuaThread thread = this.thread.get();
		return thread != null ? DebugHelpers.traceback(thread, 0) : "orphaned thread";
	}

	public void hookCall(DebugFrame frame) throws LuaError {
		if (inhook || hookfunc == null) return;

		inhook = true;
		try {
			hookfunc.onCall(state, this, frame);
		} catch (LuaError e) {
			throw e;
		} catch (RuntimeException e) {
			throw new LuaError(e);
		} finally {
			inhook = false;
		}
	}

	public void hookReturn() throws LuaError {
		if (inhook || hookfunc == null) return;

		inhook = true;
		try {
			hookfunc.onReturn(state, this, getStack());
		} catch (LuaError e) {
			throw e;
		} catch (RuntimeException e) {
			throw new LuaError(e);
		} finally {
			inhook = false;
		}
	}

	public void hookInstruction(DebugFrame frame) throws LuaError {
		if (inhook || hookfunc == null) return;

		inhook = true;
		try {
			hookfunc.onCount(state, this, frame);
		} catch (RuntimeException e) {
			throw new LuaError(e);
		} finally {
			inhook = false;
		}
	}

	public void hookLine(DebugFrame frame, int oldLine, int newLine) throws LuaError {
		if (inhook || hookfunc == null) return;

		inhook = true;
		try {
			hookfunc.onLine(state, this, frame, oldLine, newLine);
		} catch (RuntimeException e) {
			throw new LuaError(e);
		} finally {
			inhook = false;
		}
	}
}
