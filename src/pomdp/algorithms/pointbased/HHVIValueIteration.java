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
 	
 	ArrayList<MachineState> fsc = new ArrayList<MachineState>();//后来加的
 	double gamma = 0.95;  //折扣因子
	protected int maxIterations =500;  //规定最多迭代次数
	protected int iterations =0; //当前迭代次数
	protected double minWidth = Double.MAX_VALUE; //本次迭代上下界的最小差值
	protected double threshold = 0.0; //裁剪信念点的增加阈值
 	public static final double EPSILON = 0.5;
 	protected double SumDelta = 0.0;
 	protected double dDelta = 1.0;
 	
 	protected double maxADR = -Integer.MAX_VALUE; //当前最大ADR
	
	protected long maxExecutionTime = 20*60; //20min,秒
	 	 
 	public HHVIValueIteration( POMDP pomdp ){
		super(pomdp);
		
		m_itCurrentIterationPoints = null;
		m_bRandomizedActions = true;
		
		m_vfUpperBound = new JigSawValueFunction( pomdp, m_vfMDP, true ); //初始化上界
		
		
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
	    //收敛时间
		long lCPUTimeBefore=0, lCPUTimeAfter = 0, lCPUTimeTotal = 0;
		Pair<Double, Double> pComputedADRs = new Pair<Double, Double>(new Double(0.0), new Double(0.0));
	    double width = 0.0;
	    
	    //初始化信念点集合B
	  	BeliefStateVector<BeliefState>	vBeliefPoints = new BeliefStateVector<BeliefState>();
	  		
	  	//把b0加入B
	  	/* initialize the list of belief points with the initial belief state */
	  	vBeliefPoints.add(null, m_pPOMDP.getBeliefStateFactory().getInitialBeliefState() );
	    
	    boolean isconvergence = false;
	    int cBeliefPoints = 0;
	    
	    //开始迭代直至收敛
	    while(!isconvergence){
	    	//本次循环开始时间
			lCPUTimeBefore = JProf.getCurrentThreadCpuTimeSafe();
			
	    	cBeliefPoints = vBeliefPoints.size();
	    	//更新前的信念点集
	    	vBeliefPoints = expandPBVI( vBeliefPoints );  //点集的扩张，增加点扩张时条件的判断
	    	if( vBeliefPoints.size() == cBeliefPoints ){
	    		isconvergence = true;
	    	}
	   
	    	//更新上界和下界，dDelta为下界更新前后最大的提升量
			dDelta = improveValueFunction( vBeliefPoints ); 
    	
	    	iterations++;	    	
	    	
	    	//ADR
	    	pComputedADRs = CalculateADRConvergence( m_pPOMDP, pComputedADRs); 
	    	if(((Number) pComputedADRs.first()).doubleValue()>maxADR){
	    		maxADR = ((Number) pComputedADRs.first()).doubleValue();
	    	}
	    	
	    	//本次循环结束时间
	    	lCPUTimeAfter = JProf.getCurrentThreadCpuTimeSafe();
	    	//本次循环使用时间
	    	lCPUTimeTotal += (lCPUTimeAfter - lCPUTimeBefore);	    	
	    	if(iterations>=maxIterations||lCPUTimeTotal/1000000000>=maxExecutionTime){
	    		isconvergence = true;
	    	}
	    		    	
	    	Logger.getInstance().logln( "Iteration: " + iterations  +
						" |Vn|: = " + m_vValueFunction.size() +
						" |B|: = " + vBeliefPoints.size() +
						" Delta: = " + round( dDelta, 4 ) +
						" CurrentTotalTime: " + lCPUTimeTotal / 1000000000 +"seconds" );

	    	//在backup之后对信念点集进行裁剪
		    if(!isconvergence){ 
		    	for(int index = 0;index<vBeliefPoints.size();index++){
		    		width = width(vBeliefPoints.get(index));
		    		//计算本次迭代的最小上下界差值
		    		if(width<minWidth){
		    			minWidth = width;
		    		}
		    		//裁剪去值函数更新后上下界差值小于阈值的信念点
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
		//扩充后的B，原先的B中内容已经在这里
		BeliefStateVector<BeliefState> vExpanded = new BeliefStateVector<BeliefState>( vBeliefPoints );
		Iterator it = vBeliefPoints.iterator();
		//临时变量，存放当前用来扩充的b
		BeliefState bsCurrent = null;
		//临时变量，存放得到的最远b
		BeliefState bsNext = null;

		//设置不需要缓存b
		boolean bPrevious = m_pPOMDP.getBeliefStateFactory().cacheBeliefStates( false );
		//每次扩充100个b
		int beliefsize = vBeliefPoints.size() + 100<vBeliefPoints.size()*2?vBeliefPoints.size() + 100:vBeliefPoints.size()*2;
		while( vExpanded.size() < beliefsize){
			//是从扩充后B中随机取个b，计算它的最远后继！！和标准PBVI中expand不同
			//一个原因：保证能够扩充100个b
			bsCurrent = vExpanded.elementAt( m_rndGenerator.nextInt( vExpanded.size() ) );	
			
			//计算最远的后继
			bsNext = m_pPOMDP.getBeliefStateFactory().computeLimitedFarthestSuccessor( vBeliefPoints, bsCurrent,iterations,m_vfUpperBound,m_vValueFunction,m_dEpsilon,gamma,threshold);
			if( ( bsNext != null ) && ( !vExpanded.contains( bsNext ) ) )
				vExpanded.add(bsCurrent, bsNext);
		}
		//设置回原来的值，是否要缓存b
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
		
		double maxUpperDecline = 0.0,upperDecline = 0.0; //上界下降值
		BeliefState upperState = null; //上界下降值最大的信念点

		boolean bPrevious = m_pPOMDP.getBeliefStateFactory().cacheBeliefStates( false );
		
		if( m_itCurrentIterationPoints == null )
			m_itCurrentIterationPoints = vBeliefPoints.getTreeDownUpIterator();
		dMaxDelta = 0.0;
		
		//迭代所有的b
		while( m_itCurrentIterationPoints.hasNext() ){
			//当前的b
			bsCurrent= (BeliefState) m_itCurrentIterationPoints.next();
			//当前b对应的最大α
			avCurrentMax = m_vValueFunction.getMaxAlpha( bsCurrent );
			//backup操作后的α
			avBackup = backup( bsCurrent );
			
			//计算backup前后，该b点value之差
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
			
			//如果有提升，才会增加新的α
			if(dDelta >= 0){
				m_vValueFunction.addPrunePointwiseDominated( avBackup );
			    SumDelta += dDelta;
			}
			
			//更新上界
			upperDecline = m_vfUpperBound.updateValue(bsCurrent);
			//获得本次更新上界下降的最大值
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

