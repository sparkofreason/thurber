package thurber.java;

import clojure.lang.Var;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.state.StateSpec;
import org.apache.beam.sdk.state.StateSpecs;
import org.apache.beam.sdk.state.TimeDomain;
import org.apache.beam.sdk.state.Timer;
import org.apache.beam.sdk.state.TimerSpec;
import org.apache.beam.sdk.state.TimerSpecs;
import org.apache.beam.sdk.state.ValueState;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;

public final class TDoFn_Stateful extends DoFn<Object, Object> {

    private final Var fn, timerFn;
    private final Object[] args, timerArgs;

    @StateId("state")
    private final StateSpec<ValueState<Object>> stateSpec =
        StateSpecs.value(Core.nippy_deref_);

    @TimerId("timer")
    private final TimerSpec timerSpec
        = TimerSpecs.timer(TimeDomain.EVENT_TIME);

    public TDoFn_Stateful(Var fn, Var timerFn, Object... timerArgs) {
        this(fn, timerFn, new Object[]{}, timerArgs);
    }

    public TDoFn_Stateful(Var fn, Var timerFn, Object[] args, Object[] timerArgs) {
        this.fn = fn;
        this.timerFn = timerFn;
        this.args = args;
        this.timerArgs = timerArgs;
    }

    @Setup
    public void setup() {
        Core.require_(fn);
    }

    @ProcessElement
    public void processElement(PipelineOptions options, ProcessContext context, BoundedWindow window,
                               @StateId("state") ValueState<Object> state, @TimerId("timer") Timer timer) {
        TDoFn.execute(fn, args, options, context, window, state, timer, null);
    }

    @OnTimer("timer")
    public void onTimer(PipelineOptions options, OnTimerContext context, @TimerId("timer") Timer timer,
                        @StateId("state") ValueState<Object> state) {
        TDoFn.execute(timerFn, timerArgs, options, null, null, state, timer, context);
    }

}
