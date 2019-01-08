package som.interpreter.actors;

import java.util.concurrent.CompletableFuture;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

import bd.primitives.nodes.PreevaluatedExpression;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.SomException;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.asyncstacktraces.ShadowStackEntry;


public class ReceivedMessage extends ReceivedRootNode {

  @Child protected PreevaluatedExpression onReceive;

  private final SSymbol selector;

  public ReceivedMessage(final AbstractMessageSendNode onReceive,
      final SSymbol selector, final SomLanguage lang) {
    super(lang, onReceive.getSourceSection(), null);
    this.onReceive = onReceive;
    this.selector = selector;
    assert onReceive.getSourceSection() != null;
  }

  @Override
  public String getName() {
    return selector.toString();
  }

  @Override
  protected Object executeBody(final VirtualFrame frame, final EventualMessage msg,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(msg.args);
    assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || entry != null;
    ShadowStackEntry resolutionEntry =
        ShadowStackEntry.createAtPromiseResolution(entry, (ExpressionNode) onReceive);

    try {
      // We null the ShadowStackEntry which is going to be set later on depending on target
      // instead of duplicating it.
      msg.args[msg.args.length - 1] = null;
      Object result = onReceive.doPreEvaluated(frame, msg.args);

      resolvePromise(frame, msg.resolver, result,
          resolutionEntry, haltOnResolver, haltOnResolution);
    } catch (SomException exception) {
      errorPromise(frame, msg.resolver, exception.getSomObject(),
          resolutionEntry, haltOnResolver, haltOnResolution);
    }
    return null;
  }

  @Override
  public String toString() {
    return "RcvdMsg(" + selector.toString() + ")";
  }

  public static final class ReceivedMessageForVMMain extends ReceivedMessage {
    private final CompletableFuture<Object> future;

    public ReceivedMessageForVMMain(final AbstractMessageSendNode onReceive,
        final SSymbol selector, final CompletableFuture<Object> future,
        final SomLanguage lang) {
      super(onReceive, selector, lang);
      this.future = future;
    }

    @Override
    protected Object executeBody(final VirtualFrame frame, final EventualMessage msg,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      Object result = onReceive.doPreEvaluated(frame, msg.args);
      future.complete(result);
      return result;
    }
  }

  public static final class ReceivedCallback extends ReceivedRootNode {
    @Child protected DirectCallNode onReceive;

    private final Invokable onReceiveMethod;

    public ReceivedCallback(final SInvokable onReceive) {
      super(SomLanguage.getLanguage(onReceive.getInvokable()),
          onReceive.getSourceSection(), null);
      this.onReceive = Truffle.getRuntime().createDirectCallNode(onReceive.getCallTarget());
      this.onReceiveMethod = onReceive.getInvokable();
    }

    @Override
    public String getName() {
      return onReceiveMethod.getName();
    }

    @Override
    protected Object executeBody(final VirtualFrame frame, final EventualMessage msg,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      ShadowStackEntry entry = SArguments.getShadowStackEntry(msg.args);
      assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || entry != null;
      ShadowStackEntry resolutionEntry =
          ShadowStackEntry.createAtPromiseResolution(entry, onReceiveMethod.getRoot());

      try {
        Object result = onReceive.call(msg.args);
        resolvePromise(frame, msg.resolver, result, resolutionEntry,
            haltOnResolver, haltOnResolution);
      } catch (SomException exception) {
        errorPromise(frame, msg.resolver, exception.getSomObject(),
            resolutionEntry, haltOnResolver, haltOnResolution);
      }
      return null;
    }
  }
}
