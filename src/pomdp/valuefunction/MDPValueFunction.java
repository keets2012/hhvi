package pomdp.valuefunction;

import java.io.Serializable;

import pomdp.algorithms.PolicyStrategy;
import pomdp.environments.POMDP;
import pomdp.utilities.AlphaVector;
import pomdp.utilities.BeliefState;
import pomdp.utilities.RandomGenerator;
import pomdp.utilities.datastructures.DoubleVector;


public class MDPValueFunction extends PolicyStrategy implements Serializable {
	protected LinearValueFunctionApproximation m_vValueFunction;
	protected POMDP m_pPOMDP;
	protected int m_cObservations;
	protected int m_cStates;
	protected int m_cActions;
	protected double m_dGamma;
	protected DoubleVector m_adValues;
	protected AlphaVector m_avBestActions;
	protected boolean m_bConverged;
	protected boolean m_bLoaded;
	protected RandomGenerator m_rndGenerator;
	
	public MDPValueFunction( POMDP pomdp, double dExplorationRate ){
		m_pPOMDP = pomdp;
		m_vValueFunction = new LinearValueFunctionApproximation( 0.0001, false );
		m_cStates = m_pPOMDP.getStateCount();
		m_cActions = m_pPOMDP.getActionCount();
		m_cObservations = m_pPOMDP.getObservationCount();
		m_dGamma = m_pPOMDP.getDiscountFactor();
		m_adValues = new DoubleVector( m_cStates );
		m_avBestActions = null;
		m_bConverged = false;
		m_bLoaded = false;
		m_rndGenerator = new RandomGenerator( "MDPVI", 0 );
	}

	@Override
	public int getAction(BeliefState bsCurrent) {
		return 0;
	}
	
	public double getValue( int iState ){
		//return m_vValues.elementAt( iState );
		//return m_adValues[iState];
		return m_adValues.elementAt( iState );
	}
}
