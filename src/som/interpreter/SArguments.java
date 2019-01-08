package som.interpreter;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.nodes.ExpressionNode;
import som.primitives.SizeAndLengthPrim;
import som.primitives.arrays.AtPrim;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SBlock;
import tools.asyncstacktraces.ShadowStackEntry;
import tools.asyncstacktraces.ShadowStackEntryLoad;


public final class SArguments {

  public static final int RCVR_IDX = 0;

  private static Object[] args(final Frame frame) {
    return frame.getArguments();
  }

  public static Object arg(final Frame frame, final int index) {
    return args(frame)[index];
  }

  public static Object rcvr(final Frame frame) {
    return arg(frame, RCVR_IDX);
  }

  /**
   * Create a new array from an SArguments array that contains only the true
   * arguments and excludes the receiver. This is used for instance for
   * #doesNotUnderstand (#dnu).
   */
  public static SImmutableArray getArgumentsWithoutReceiver(final Object[] arguments) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      if (arguments.length == 2) {
        return new SImmutableArray(0, Classes.valueArrayClass);
      }
    } else {
      if (arguments.length == 1) {
        return new SImmutableArray(0, Classes.valueArrayClass);
      }
    }
    Object[] argsArr = getPlainArgumentWithoutReceiver(arguments);
    return new SImmutableArray(argsArr, Classes.valueArrayClass);
  }

  /**
   * Create a new array and copy inside the arguments without the receiver.
   * Used for FFI calls and DNUs.
   */
  public static Object[] getPlainArgumentWithoutReceiver(final Object[] arguments) {
    int rcvrIdx = 0; // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == rcvrIdx;
    assert arguments.length >= 1; // <- that's the receiver
    int argsSize = arguments.length - 1;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argsSize--;
    }
    Object[] argsArr = new Object[argsSize];

    System.arraycopy(arguments, 1, argsArr, 0, argsArr.length);
    return argsArr;
  }

  public static Object[] allocateArgumentsArray(final ExpressionNode[] argumentNodes) {
    int argLength = argumentNodes.length;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argLength++;
    }
    return new Object[argLength];
  }

  public static Object[] getPlainArgumentsWithReceiver(final Object receiver,
      final SArray args, final SizeAndLengthPrim size, final AtPrim at,
      final ExpressionNode expression,
      final ShadowStackEntryLoad entryLoad,
      final VirtualFrame frame) {
    int argSize = (int) (size.executeEvaluated(args) + 1);
    int defaultArgSize = argSize;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argSize++;
    }

    Object[] result = new Object[argSize];
    result[0] = receiver;
    for (int i = 1; i < defaultArgSize; i++) {
      result[i] = at.executeEvaluated(null, args, (long) i);
    }

    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      entryLoad.loadShadowStackEntry(result, expression, frame, false);
    }
    return result;
  }

  public static Object[] getPlainNoArgumentsWithReceiver(final Object receiver,
      final ExpressionNode expression,
      final ShadowStackEntryLoad entryLoad,
      final VirtualFrame frame) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      Object[] arguments = new Object[] {receiver, null};
      entryLoad.loadShadowStackEntry(arguments, expression, frame, false);
      return arguments;
    } else {
      return new Object[] {receiver};
    }
  }

  public static Object[] getPlain1ArgumentWithReceiver(final Object receiver,
      final Object firstArg,
      final ExpressionNode expression,
      final ShadowStackEntryLoad entryLoad,
      final VirtualFrame frame) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      Object[] arguments = new Object[] {receiver, firstArg, null};
      entryLoad.loadShadowStackEntry(arguments, expression, frame, false);
      return arguments;
    } else {
      return new Object[] {receiver, firstArg};
    }
  }

  public static Object[] getPlain2ArgumentsWithReceiver(final Object receiver,
      final Object firstArg,
      final Object secondArg,
      final ExpressionNode expression,
      final ShadowStackEntryLoad entryLoad,
      final VirtualFrame frame) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      Object[] arguments = new Object[] {receiver, firstArg, secondArg, null};
      entryLoad.loadShadowStackEntry(arguments, expression, frame, false);
      return arguments;
    } else {
      return new Object[] {receiver, firstArg, secondArg};
    }
  }

  public static Object[] getPlainArguments(final Object[] args) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      Object[] newArgs = new Object[args.length + 1];
      for (int i = 0; i < args.length; i++) {
        newArgs[i] = args[i];
      }
      return newArgs;
    } else {
      return args;
    }
  }

  public static Object[] getPromiseCallbackArgumentArray(final SBlock callback) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      return new Object[] {callback, null, null};
    } else {
      return new Object[] {callback, null};
    }
  }

  public static void setShadowStackEntryWithCache(final Object[] arguments,
      final Node expression,
      final ShadowStackEntryLoad entryLoad,
      final VirtualFrame frame,
      final boolean async) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      entryLoad.loadShadowStackEntry(arguments, expression, frame, async);
    }
  }

  public static ShadowStackEntry instantiateTopShadowStackEntry(final Node expr) {
    return instantiateShadowStackEntry(null, expr, false);
  }

  public static ShadowStackEntry instantiateShadowStackEntry(final ShadowStackEntry previous,
      final Node expr, final boolean async) {
    if (async) {
      return ShadowStackEntry.createAtAsyncSend(previous, expr);
    } else {
      return ShadowStackEntry.create(previous, expr);
    }
  }

  public static ShadowStackEntry getShadowStackEntry(final VirtualFrame frame) {
    Object[] args = frame.getArguments();
    return getShadowStackEntry(args);
  }

  public static ShadowStackEntry getShadowStackEntry(final Object[] args) {
    Object maybeShadowStack = args[args.length - 1];
    if (maybeShadowStack instanceof ShadowStackEntry) {
      return (ShadowStackEntry) maybeShadowStack;
    }
    return null;
  }

  public static void setShadowStackEntry(final Object[] args,
      final ShadowStackEntry entry) {
    // assert args[args.length - 1] == null : "Assume shadow stack entry is not already set.";
    args[args.length - 1] = entry;
  }

  public static int getLengthWithoutShadowStack(final Object[] arguments) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      return arguments.length - 1;
    } else {
      return arguments.length;
    }
  }
}
