package pomdp.algorithms.pointbased;


import pomdp.algorithms.ValueIteration;
import pomdp.environments.POMDP;
import pomdp.utilities.*;
import pomdp.valuefunction.JigSawValueFunction;
import pomdp.valuefunction.LinearValueFunctionApproximation;

import java.util.ArrayList;
import java.util.Iterator;

public class HHVIValueIteration extends ValueIteration{

	protected Iterator<BeliefState> m_itCurrentIterationPoints;
    protected boolean m_bSingleValueFunction = true;
 	protected boolean m_bRandomizedActions;
 	protected double m_dFilteredADR = 0.0;
 	protected JigSawValueFunction m_vfUpperBound;
 	
 	ArrayList<MachineState> fsc = new ArrayList<MachineState>();//�����ӵ�
 	double gamma = 0.95;  //�ۿ�����
	protected int maxIterations =500;  //�涨����������
	protected int iterations =0; //��ǰ��������
	protected double minWidth = Double.MAX_VALUE; //���ε������½����С��ֵ
	protected double threshold = 0.0; //�ü�������������ֵ
 	public static final double EPSILON = 0.5;
 	protected double SumDelta = 0.0;
 	protected double dDelta = 1.0;
 	
 	protected double maxADR = -Integer.MAX_VALUE; //��ǰ���ADR
	
	protected long maxExecutionTime = 20*60; //20min,��
	 	 
 	public HHVIValueIteration( POMDP pomdp ){
		super(pomdp);
		
		m_itCurrentIterationPoints = null;
		m_bRandomizedActions = true;
		
		m_vfUpperBound = new JigSawValueFunction( pomdp, m_vfMDP, true ); //��ʼ���Ͻ�
		
		
	}

	public HHVIValueIteration( POMDP pomdp, boolean bRandomizedActionExpansion ){
		super(pomdp);
		
		m_itCurrentIterationPoints = null;
		m_bRandomizedActions = bRandomizedActionExpansion;
		
		m_vfUpperBound = new JigSawValueFunction( pomdp, m_vfMDP, true );
		
	}
	
	
	
	public int getAction(BeliefState bsCurrent) {
		return m_vValueFunction.getBestAction(bsCurrent);
	}

	
	public void NewIteration(POMDP pomdp) {
	    //����ʱ��
		long lCPUTimeBefore=0, lCPUTimeAfter = 0, lCPUTimeTotal = 0;
		Pair<Double, Double> pComputedADRs = new Pair<Double, Double>(new Double(0.0), new Double(0.0));
	    double width = 0.0;
	    
	    //��ʼ������㼯��B
	  	BeliefStateVector<BeliefState>	vBeliefPoints = new BeliefStateVector<BeliefState>();
	  		
	  	//��b0����B
	  	/* initialize the list of belief points with the initial belief state */
	  	vBeliefPoints.add(null, m_pPOMDP.getBeliefStateFactory().getInitialBeliefState() );
	    
	    boolean isconvergence = false;
	    int cBeliefPoints = 0;
	    
	    //��ʼ����ֱ������
	    while(!isconvergence){
	    	//����ѭ����ʼʱ��
			lCPUTimeBefore = JProf.getCurrentThreadCpuTimeSafe();
			
	    	cBeliefPoints = vBeliefPoints.size();
	    	//����ǰ������㼯
	    	vBeliefPoints = expandPBVI( vBeliefPoints );  //�㼯�����ţ����ӵ�����ʱ�������ж�
	    	if( vBeliefPoints.size() == cBeliefPoints ){
	    		isconvergence = true;
	    	}
	   
	    	//�����Ͻ���½磬dDeltaΪ�½����ǰ������������
			dDelta = improveValueFunction( vBeliefPoints ); 
    	
	    	iterations++;	    	
	    	
	    	//ADR
	    	pComputedADRs = CalculateADRConvergence( m_pPOMDP, pComputedADRs); 
	    	if(((Number) pComputedADRs.first()).doubleValue()>maxADR){
	    		maxADR = ((Number) pComputedADRs.first()).doubleValue();
	    	}
	    	
	    	//����ѭ������ʱ��
	    	lCPUTimeAfter = JProf.getCurrentThreadCpuTimeSafe();
	    	//����ѭ��ʹ��ʱ��
	    	lCPUTimeTotal += (lCPUTimeAfter - lCPUTimeBefore);	    	
	    	if(iterations>=maxIterations||lCPUTimeTotal/1000000000>=maxExecutionTime){
	    		isconvergence = true;
	    	}
	    		    	
	    	Logger.getInstance().logln( "Iteration: " + iterations  +
						" |Vn|: = " + m_vValueFunction.size() +
						" |B|: = " + vBeliefPoints.size() +
						" Delta: = " + round( dDelta, 4 ) +
						" CurrentTotalTime: " + lCPUTimeTotal / 1000000000 +"seconds" );

	    	//��backup֮�������㼯���вü�
		    if(!isconvergence){ 
		    	for(int index = 0;index<vBeliefPoints.size();index++){
		    		width = width(vBeliefPoints.get(index));
		    		//���㱾�ε�������С���½��ֵ
		    		if(width<minWidth){
		    			minWidth = width;
		    		}
		    		//�ü�ȥֵ�������º����½��ֵС����ֵ�������
		    		if(width <  m_dEpsilon/Math.pow(gamma,iterations)){
		    			 vBeliefPoints.remove(index);
		    			 index--;
		    		 }
		    	}
		    	Logger.getInstance().logln(" Prune after backup |B|: "+vBeliefPoints.size());
		    	Logger.getInstance().logln("minWidth: "+minWidth);
		    	Logger.getInstance().logln("maxADR: "+maxADR);
		    	Logger.getInstance().logln();
		    }
		    threshold = minWidth;
	    		    	
	    	System.out.println("");
	    }
	    
	    	Logger.getInstance().logln( "Finished " + " - time : " + lCPUTimeTotal/1000000000 +"seconds"+ " |BS| = " + vBeliefPoints.size() +
					" |V| = " + m_vValueFunction.size() );
	}
	
	protected BeliefStateVector<BeliefState> expandPBVI( BeliefStateVector<BeliefState> vBeliefPoints ){
		//������B��ԭ�ȵ�B�������Ѿ�������
		BeliefStateVector<BeliefState> vExpanded = new BeliefStateVector<BeliefState>( vBeliefPoints );
		Iterator it = vBeliefPoints.iterator();
		//��ʱ��������ŵ�ǰ���������b
		BeliefState bsCurrent = null;
		//��ʱ��������ŵõ�����Զb
		BeliefState bsNext = null;

		//���ò���Ҫ����b
		boolean bPrevious = m_pPOMDP.getBeliefStateFactory().cacheBeliefStates( false );
		//ÿ������100��b
		int beliefsize = vBeliefPoints.size() + 100<vBeliefPoints.size()*2?vBeliefPoints.size() + 100:vBeliefPoints.size()*2;
		while( vExpanded.size() < beliefsize){
			//�Ǵ������B�����ȡ��b������������Զ��̣����ͱ�׼PBVI��expand��ͬ
			//һ��ԭ�򣺱�֤�ܹ�����100��b
			bsCurrent = vExpanded.elementAt( m_rndGenerator.nextInt( vExpanded.size() ) );	
			
			//������Զ�ĺ��
			bsNext = m_pPOMDP.getBeliefStateFactory().computeLimitedFarthestSuccessor( vBeliefPoints, bsCurrent,iterations,m_vfUpperBound,m_vValueFunction,m_dEpsilon,gamma,threshold);
			if( ( bsNext != null ) && ( !vExpanded.contains( bsNext ) ) )
				vExpanded.add(bsCurrent, bsNext);
		}
		//���û�ԭ����ֵ���Ƿ�Ҫ����b
		m_pPOMDP.getBeliefStateFactory().cacheBeliefStates( bPrevious );
		
		return vExpanded;
	}
	
	protected double improveValueFunction( BeliefStateVector vBeliefPoints ){
		LinearValueFunctionApproximation vNextValueFunction = new LinearValueFunctionApproximation( m_dEpsilon, true );
		BeliefState bsCurrent = null, bsMax = null;
		AlphaVector avBackup = null, avNext = null, avCurrentMax = null;
		double dMaxDelta = 1.0, dDelta = 0.0, dBackupValue = 0.0, dValue = 0.0;
		double dMaxOldValue = 0.0, dMaxNewValue = 0.0;
		int iBeliefState = 0;
		
		double maxUpperDecline = 0.0,upperDecline = 0.0; //�Ͻ��½�ֵ
		BeliefState upperState = null; //�Ͻ��½�ֵ���������

		boolean bPrevious = m_pPOMDP.getBeliefStateFactory().cacheBeliefStates( false );
		
		if( m_itCurrentIterationPoints == null )
			m_itCurrentIterationPoints = vBeliefPoints.getTreeDownUpIterator();
		dMaxDelta = 0.0;
		
		//�������е�b
		while( m_itCurrentIterationPoints.hasNext() ){
			//��ǰ��b
			bsCurrent= (BeliefState) m_itCurrentIterationPoints.next();
			//��ǰb��Ӧ������
			avCurrentMax = m_vValueFunction.getMaxAlpha( bsCurrent );
			//backup������Ħ�
			avBackup = backup( bsCurrent );
			
			//����backupǰ�󣬸�b��value֮��
			dBackupValue = avBackup.dotProduct( bsCurrent );
			dValue = avCurrentMax.dotProduct( bsCurrent );
			dDelta = dBackupValue - dValue;
			
			
			if( dDelta > dMaxDelta ){
				dMaxDelta = dDelta;
				bsMax = bsCurrent;
				dMaxOldValue = dValue;
				dMaxNewValue = dBackupValue;
			}
			
			avNext = avBackup;
			
			//������������Ż������µĦ�
			if(dDelta >= 0){
				m_vValueFunction.addPrunePointwiseDominated( avBackup );
			    SumDelta += dDelta;
			}
			
			//�����Ͻ�
			upperDecline = m_vfUpperBound.updateValue(bsCurrent);
			//��ñ��θ����Ͻ��½������ֵ
			if(upperDecline>maxUpperDecline){ 
				maxUpperDecline = upperDecline;
				upperState = bsCurrent;
			}
			
			iBeliefState++;
		}
		
		if( m_bSingleValueFunction ){
			Iterator it = vNextValueFunction.iterator();
			while( it.hasNext() ){
				avNext = (AlphaVector) it.next();
				m_vValueFunction.addPrunePointwiseDominated( avNext );
			}
		}		
		
		if( !m_itCurrentIterationPoints.hasNext() )
			m_itCurrentIterationPoints = null;
		
		Logger.getInstance().logln( "Max lowBounddelta over " + bsMax + 
				" from " + round( dMaxOldValue, 3 ) + 
				" to " + round( dMaxNewValue, 3 ) );
		
		Logger.getInstance().logln( "Max upperBounddelta over " + upperState + 
				" is "+round( maxUpperDecline, 3 ) );
		
		m_pPOMDP.getBeliefStateFactory().cacheBeliefStates( bPrevious );
		
		return dMaxDelta;
	}
	

	 public static double vecMultiply(double[] v1, TabularAlphaVector vector) {
		 if(v1==null||vector==null){
			 System.out.println(v1+" "+vector);
		 }
	        double result = 0;
	        for (int i = 0; i < v1.length; i++) {
	            result += (v1[i] * vector.valueAt(i));

	        }
	        return result;
	    }
	
	 public static double vecMultiply(double[] v1, BeliefState point) {
	        double result = 0;
	        for (int i = 0; i < v1.length; i++) {
	            result += (v1[i] * point.valueAt(i));

	        }
	        return result;
	    }
	 public static double vecMultiply(double[] v1, double[] v2) {
	        double result = 0;
	        for (int i = 0; i < v1.length; i++) {
	            result += (v1[i] * v2[i]);

	        }
	        return result;
	    }

	@Override
	public void NewIteration() {
		// TODO Auto-generated method stub
		
	}

	protected double width( BeliefState bsCurrent ){
		double dUpperValue = 0.0, dLowerValue = 0.0, dWidth = 0.0;
		dUpperValue = m_vfUpperBound.valueAt( bsCurrent );
		dLowerValue = m_vValueFunction.valueAt( bsCurrent );
		dWidth = dUpperValue - dLowerValue;			
		return dWidth;
	}

	
}

