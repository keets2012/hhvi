package pomdp.utilities;

import java.util.LinkedList;
import java.util.Queue;

import pomdp.environments.POMDP;
import pomdp.utilities.distance.L1Distance;

public class Expander {

	public static final int MAXSIZE = 1500;
	private POMDP pomdp = null;
	private static final int LEVEL = 7;//
	private static final int MAX_LEVEL = 5;
	public Expander(POMDP pomdp){
		this.pomdp = pomdp;
	}
	public BeliefStateVector<BeliefState> expand(double epsilon)
	{
		BeliefStateVector<BeliefState> vBeliefPoints = new BeliefStateVector<BeliefState>();
		Queue<BeliefState> q = new LinkedList<BeliefState>();
		BeliefState initial = pomdp.getBeliefStateFactory().getInitialBeliefState();
		vBeliefPoints.add(initial);
		q.offer(initial);
		while(!q.isEmpty())
		{
			BeliefState bs = q.poll();
			for(int iAction = 0; iAction < pomdp.getActionCount(); ++iAction)
			{
				for(int iObservation = 0; iObservation < pomdp.getObservationCount(); ++iObservation)
				{
					BeliefState next = bs.nextBeliefState(iAction, iObservation);

					if(next != null && !vBeliefPoints.contains(next))
					{
						next.setLevel(bs.getLevel() + 1);//设置层数
						
						//判断next点和任一点的距离是否大于epsilon，大于就加入点集
						boolean inRange = false;
						L1Distance distancer = new L1Distance();
						for(BeliefState beliefState:vBeliefPoints){
							if(distancer.distance(beliefState, next) < epsilon){
								 inRange = true;
								 break;
							}
						}
						if(!inRange){
							if(next.getLevel() <= LEVEL)
							{
								q.offer(next);
								vBeliefPoints.add(next);
								System.out.println(vBeliefPoints.size() + "   " + next.getLevel());
								if(vBeliefPoints.size() >= 3800)
									return vBeliefPoints;
							}
							else
							{
								System.out.println(vBeliefPoints.size());
								  return vBeliefPoints;
							}
						}
					}
				}
			}
		}
		return vBeliefPoints;
	}
}
