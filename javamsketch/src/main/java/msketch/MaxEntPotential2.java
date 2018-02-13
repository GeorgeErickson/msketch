package msketch;

import msketch.optimizer.FunctionWithHessian;

import java.util.Arrays;

/**
 * Minimizing this function yields a maxent pdf which matches the the empirical
 * moments of a dataset. The function is convex with symmetric positive definite
 * hessian and has a global stationary minimum (gradient = 0) at the solution.
 */
public class MaxEntPotential2 implements FunctionWithHessian {
    boolean isLog;
    protected int numNormalPowers;
    protected double[] d_mus;
    private double aCenter, aScale, bCenter, bScale;

    public MaxEntFunction2 getFunc() {
        return func;
    }

    private MaxEntFunction2 func;

    private int cumFuncEvals = 0;
    protected double[] lambd;
    protected double[] mus;
    protected double[] grad;
    protected double[][] hess;

    public MaxEntPotential2(
            boolean isLog,
            int numNormalPowers,
            double[] d_mus,
            double aCenter,
            double aScale,
            double bCenter,
            double bScale
    ) {
        this.isLog = isLog;
        this.numNormalPowers = numNormalPowers;
        this.d_mus = d_mus;
        this.aCenter = aCenter;
        this.aScale = aScale;
        this.bCenter = bCenter;
        this.bScale = bScale;

        this.cumFuncEvals = 0;
        int k = d_mus.length;
        this.mus = new double[k];
        this.grad = new double[k];
        this.hess = new double[k][k];
    }

    @Override
    public int dim() {
        return d_mus.length;
    }

    private MaxEntFunction2 setFunction(double[] lambd) {
        this.lambd = lambd;
        int k = lambd.length;

        double[] aCoeffs = new double[numNormalPowers];
        double[] bCoeffs = new double[k - numNormalPowers + 1];
        for (int i = 0; i < k; i++) {
            if (i < numNormalPowers) {
                aCoeffs[i] = lambd[i];
            } else {
                bCoeffs[i - numNormalPowers + 1] = lambd[i];
            }
        }
        this.func = new MaxEntFunction2(
                isLog,
                aCoeffs,
                bCoeffs,
                aCenter,
                aScale,
                bCenter,
                bScale
        );
        return this.func;
    }

    @Override
    public void computeOnlyValue(double[] point, double tol) {
        int k = lambd.length;
        setFunction(point);
        this.mus[0] = func.zerothMoment(tol);
        cumFuncEvals += func.getNumFuncEvals();
    }

    @Override
    public void computeAll(double[] lambd, double tol) {
        int k = lambd.length;
        setFunction(lambd);
        double[][] pairwiseMoments = func.getPairwiseMoments(tol);
        cumFuncEvals += func.getNumFuncEvals();

        for (int i = 0; i < k; i++) {
            if (i < numNormalPowers) {
                this.mus[i] = pairwiseMoments[i][0];
            } else {
                this.mus[i] = pairwiseMoments[i+1][0];
            }
        }
        for (int i = 0; i < k; i++) {
            this.grad[i] = this.mus[i] - this.d_mus[i];
        }

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                int curI = i;
                int curJ = j;
                if (curI >= numNormalPowers) {
                    curI++;
                }
                if (curJ >= numNormalPowers) {
                    curJ++;
                }
                this.hess[i][j] = pairwiseMoments[curI][curJ];
            }
        }
    }

    @Override
    public double getValue() {
        double sum = 0.0;
        int k = d_mus.length;
        for (int i = 0; i < k; i++) {
            sum += lambd[i] * d_mus[i];
        }
        return this.mus[0] - sum;
    }

    @Override
    public double[] getGradient() {
        return grad;
    }

    @Override
    public double[][] getHessian() {
        return hess;
    }

    public int getCumFuncEvals() {
        return cumFuncEvals;
    }
}