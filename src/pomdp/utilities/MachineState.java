package pomdp.utilities;

import java.io.Serializable;
import java.util.HashMap;

public class MachineState implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int action;
    private AlphaVector vec;
    public HashMap outwardLink = new HashMap();
    public BeliefStateVector<BeliefState> pointGroup = new BeliefStateVector<BeliefState>();
    public BeliefStateVector<BeliefState> samplepoints = new BeliefStateVector<BeliefState>();

   
    
    public int getAction()
    {
    	return action;
    }
    
    public BeliefStateVector<BeliefState> getSamplePoints()
    {
    	return samplepoints;
    }
    
    public BeliefStateVector<BeliefState> getPointGroup()
    {
    	return pointGroup;
    }
    public void setAction(int action)
    {
    	this.action = action;
    }
    public AlphaVector getVec()
    {
    	return vec;
    }
    
    public void setVec(AlphaVector vec)
    {
    	this.vec = vec;
    }
}
