package wyvc.builder;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;


public class CompilerLogger {
	public static final class CompilerException extends Exception {
		private static final long serialVersionUID = -1744237848876954932L;

		public final CompilerError error;

		public CompilerException(CompilerError error) {
			this.error = error;
		}
	}

	public static enum CompilerMessageType {
		Error, Warning, Notice, Debug
	}

	private static abstract class CompilerMessage {
		public final CompilerMessageType type;

		public CompilerMessage(CompilerMessageType type) {
			this.type = type;
		}

		public abstract String info();
	}

	public static abstract class CompilerError extends CompilerMessage {
		public CompilerError() {
			super(CompilerMessageType.Error);
		}
	}

	public static abstract class CompilerWarning extends CompilerMessage {
		public CompilerWarning() {
			super(CompilerMessageType.Warning);
		}
	}

	public static abstract class CompilerNotice extends CompilerMessage {
		public CompilerNotice() {
			super(CompilerMessageType.Notice);
		}
	}

	public static abstract class CompilerDebug extends CompilerMessage {
		public CompilerDebug() {
			super(CompilerMessageType.Debug);
		}
	}

	public static class UnsupportedCompilerError extends CompilerError {
		public String info() {
			return "This feature is currently unsupported";
		}
	}

	public static class UnboundLoopCompilerWarning extends CompilerWarning {
		@Override
		public String info() {
			return "This loop is unbound and prevent any time garantee";
		}
	}






	private final PrintStream err;
	private List<CompilerMessage> messages = new ArrayList<>();
	private Map<CompilerMessageType, Integer> types = new HashMap<>();
	private boolean debugOut = true;

	public CompilerLogger() {
		err = System.err;
	}

	public CompilerLogger(PrintStream err) {
		this.err = err;
	}

	public boolean has(CompilerMessageType type) {
		return types.getOrDefault(type, 0) != 0;
	}

	public void printMessages() {
		// TODO nb de warning et cie
	}

	public <T> T addMessage(CompilerMessage message, T value) {
		addMessage(message);
		return value;
	}

	public CompilerLogger addMessage(CompilerMessage message) {
		messages.add(message);
		err.println("┌─── "+message.type.name() + " ────────────────────────────────────────────────────────────────".substring(0, 60-message.type.name().length()));
		for (String i : message.info().split("\n"))
			err.println("│"+i);
		types.put(message.type, types.getOrDefault(message.type, 0) + 1);
		return this;
	}

	public void debug(String message) {
		if (debugOut )
			for (String i : message.split("\n"))
				err.println("    > "+i);
	}



	public static class LoggedBuilder {
		public final CompilerLogger logger;

		public LoggedBuilder() {
			logger = new CompilerLogger();
		}

		public LoggedBuilder(LoggedBuilder parent) {
			logger = parent.logger;
		}

		public LoggedBuilder(CompilerLogger logger) {
			this.logger = logger;
		}

		public <T> T addMessage(CompilerMessage message, T defaultValue) {
			addMessage(message);
			return defaultValue;
		}
		public void addMessage(CompilerMessage message) {
			logger.addMessage(message);
		}

		public boolean has(CompilerMessageType type) {
			return logger.has(type);
		}

		public void debug(String message){
			logger.debug(message);
		}


		protected String level = "";
		protected Stack<String> block = new Stack<>();
		protected void writeLevel(boolean open) {
			String a = "──────────────────────────────────────────────────";
			debug(level+(open ? "┌─" : "└─")+a.substring(0, Math.max(0, 30-level.length()))+" "+
					block.lastElement()+" "+a.substring(0, Math.max(0, 35-block.lastElement().length())));
		}
		protected void openLevel(String n) {
			block.push(n);
			writeLevel(true);
			level = level+"│ ";
		}
		public void debugLevel(String message){
			for (String e : message.split("\n"))
				debug(level+e);
		}
		protected void closeLevel() {
			level = level.substring(0, Math.max(0,level.length()-2));
			writeLevel(false);
			block.pop();
		}
		protected <T> T end(T a) {
//			debugLevel("R " + a);
/**/			closeLevel();
			return a;
		}
	}

	public static class LoggedContainer {
		public final CompilerLogger logger;

		public LoggedContainer(CompilerLogger logger) {
			this.logger = logger;
		}
	}


}
