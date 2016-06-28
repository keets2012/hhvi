package pomdp;

import java.text.SimpleDateFormat;
import java.util.Date;

import pomdp.algorithms.pointbased.HHVIValueIteration;
import pomdp.environments.POMDP;
import pomdp.utilities.Logger;

public class POMDPSolver {

	public static void main(String[] args) {
		String sPath =  "Models/";// �õ�model·��
		String sModelName = "tagAvoid";// model��
		String sMethodName = "HHVI";//������
		Logger.getInstance().setOutput(true);//�������
		Logger.getInstance().setSilent(false);//�������������̨
		try {
			String sOutputDir = "logs/POMDPSolver/";// ���·��
			
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm");						
			String sFileName = sModelName + "_" + sMethodName+ "_" + sdf.format(new Date())+ ".txt";// ����ļ���
			Logger.getInstance().setOutputStream(sOutputDir, sFileName);
		} catch (Exception e) {
			System.err.println(e);
		}

		POMDP pomdp = null;
		try {
			pomdp = new POMDP();
			pomdp.load(sPath + sModelName + ".POMDP");// ����pomdpģ��,  ���������������е�Model
			
			//������ر�ֵ����С�ر�ֵ
    	    //Logger.getInstance().logln("max is " + pomdp.getMaxR() + " min is " + pomdp.getMinR());
		} catch (Exception e) {
			Logger.getInstance().logln(e);
			e.printStackTrace();
			System.exit(0);
		}
		try{
			HHVIValueIteration iteration = new HHVIValueIteration(pomdp);//��ʼ����
			iteration.NewIteration(pomdp);
		}catch (Exception e) {
			Logger.getInstance().logln(e);
			e.printStackTrace();
			System.exit(0);
		}
	}
}
