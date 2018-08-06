package tools.asyncStackTraces;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Invokable;
import som.interpreter.actors.EventualMessage;
import tools.SourceCoordinate;


public final class AsyncTraceEntry {

  protected final String          trace;
  protected final AsyncTraceEntry prevEntry;

  public AsyncTraceEntry() {
    AsyncTraceEntry prev = null;
    trace = this.getLocalStackTrace();
    try {
      EventualMessage current = EventualMessage.getCurrentExecutingMessage();
      if (current != null) {
        prev = current.getAsyncTraceEntry();
      }
    } catch (Exception e) {
      // Cast error - no prev.
    }
    prevEntry = prev;
  }

  public String getLocalStacktrace() {
    return trace;
  }

  public AsyncTraceEntry getPrevEntry() {
    return prevEntry;
  }

  @TruffleBoundary
  protected String getLocalStackTrace() {
    ArrayList<String> method = new ArrayList<String>();
    ArrayList<String> location = new ArrayList<String>();
    int[] maxLengthMethod = {0};
    boolean[] first = {true};

    Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
      @Override
      public Object visitFrame(final FrameInstance frameInstance) {
        RootCallTarget ct = (RootCallTarget) frameInstance.getCallTarget();

        if (!(ct.getRootNode() instanceof Invokable)) {
          return null;
        }

        Invokable m = (Invokable) ct.getRootNode();

        String id = m.getName();
        method.add(id);
        maxLengthMethod[0] = Math.max(maxLengthMethod[0], id.length());
        Node callNode = frameInstance.getCallNode();
        if (callNode != null || first[0]) {
          SourceSection nodeSS;
          if (first[0]) {
            first[0] = false;
            nodeSS = null;
          } else {
            nodeSS = callNode.getEncapsulatingSourceSection();
          }
          if (nodeSS != null) {
            location.add(nodeSS.getSource().getName()
                + SourceCoordinate.getLocationQualifier(nodeSS));
          } else {
            location.add("");
          }
        } else {
          location.add("");
        }

        return null;
      }
    });

    StringBuilder sb = new StringBuilder();
    try {
      sb.append("Actor "
          + EventualMessage.getActorCurrentMessageIsExecutionOn().getId() + "\n");
    } catch (Exception e) {
      sb.append("Main Actor\n");
    }
    for (int i = method.size() - 1; i >= 0; i--) {
      sb.append(String.format("\t%1$-" + (maxLengthMethod[0] + 4) + "s",
          method.get(i)));
      sb.append(location.get(i));
      sb.append('\n');
    }

    return sb.toString();
  }
}
