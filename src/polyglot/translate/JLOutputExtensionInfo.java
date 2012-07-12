package polyglot.translate;

import polyglot.ast.NodeFactory;
import polyglot.ext.jl5.JL5Options;
import polyglot.frontend.EmptyPass;
import polyglot.frontend.ExtensionInfo;
import polyglot.frontend.JLExtensionInfo;
import polyglot.frontend.JLScheduler;
import polyglot.frontend.Job;
import polyglot.frontend.Pass;
import polyglot.frontend.Scheduler;
import polyglot.frontend.goals.Goal;
import polyglot.frontend.goals.SourceFileGoal;
import polyglot.main.Options;

public class JLOutputExtensionInfo extends JLExtensionInfo {
	final protected ExtensionInfo parent;

	public JLOutputExtensionInfo(ExtensionInfo parent) {
		this.parent = parent;
	}

	public Scheduler createScheduler() {
		return new JLOutputScheduler(this);
	}

	protected Options createOptions() {
		return parent.getOptions();
	}

	static protected class JLOutputScheduler extends JLScheduler {
		public JLOutputScheduler(ExtensionInfo extInfo) {
			super(extInfo);
		}

		public Goal Parsed(Job job) {
			return internGoal(new SourceFileGoal(job) {
				public Pass createPass(ExtensionInfo extInfo) {
					return new EmptyPass(this);
				}
			});
		}
	}
}