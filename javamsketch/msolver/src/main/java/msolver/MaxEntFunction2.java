package msolver;

import msolver.chebyshev.ChebyshevPolynomial;
import msolver.chebyshev.CosScaledFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.RombergIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

public class MaxEntFunction2 implements UnivariateFunction {
    private double[] aCoeffs;
    private double[] bCoeffs;
    private double aCenter, aScale, bCenter, bScale;
    private boolean isLog;
    // starting with order 1
    private ChebyshevPolynomial[] gPolys;

    private ChebyshevPolynomial aPoly;
    private ChebyshevPolynomial bPoly;
    private ChebyshevPolynomial[] bases;

    private int numFuncEvals;

    public int getNumFuncEvals() {
        return numFuncEvals;
    }

    public String toString() {
        return Arrays.toString(aCoeffs)+":"+Arrays.toString(bCoeffs)+":"+
                aCenter+","+aScale+","+bCenter+","+bScale+"."+isLog;
    }

    public MaxEntFunction2(
            boolean isLog,
            double[] aCoeffs,
            double[] bCoeffs,
            double aCenter,
            double aScale,
            double bCenter,
            double bScale
    ) {
        this.isLog = isLog;
        this.aCenter = aCenter;
        this.aScale = aScale;
        this.bCenter = bCenter;
        this.bScale = bScale;
        setCoeffs(aCoeffs, bCoeffs);

        this.bases = new ChebyshevPolynomial[2*(aCoeffs.length+bCoeffs.length)];
        for (int i = 0; i < bases.length; i++) {
            bases[i] = ChebyshevPolynomial.basis(i);
        }
        this.gPolys = new ChebyshevPolynomial[bCoeffs.length-1];
        for (int i = 0; i < gPolys.length; i++) {
            gPolys[i] = ChebyshevPolynomial.fit(new GFunction(
                    i+1, isLog,
                    aCenter, aScale, bCenter, bScale
            ), 1e-9
            );
        }
        numFuncEvals = 0;
    }

    public void setCoeffs(
            double[] aCoeffs,
            double[] bCoeffs
    ) {
        this.aCoeffs = aCoeffs;
        this.bCoeffs = bCoeffs;
        this.aPoly = new ChebyshevPolynomial(aCoeffs);
        this.bPoly = new ChebyshevPolynomial(bCoeffs);
    }

    public double valueRaw(double x) {
        return value((x - aCenter) / aScale);
    }

    private double getBGX(double y) {
        double x = y*aScale+aCenter;
        double gX;
        if (isLog) {
            gX = Math.log(x);
        } else {
            gX = Math.exp(x);
        }
        return (gX - bCenter) / bScale;
    }

    @Override
    public double value(double y) {
        double scaledBGX = getBGX(y);
        double expValue = aPoly.value(y) + bPoly.value(scaledBGX);
        return Math.exp(expValue);
    }

    public double zerothMoment(double tol) {
        ChebyshevPolynomial pApprox = ChebyshevPolynomial.fit(this, tol);
        numFuncEvals += pApprox.getNumFitEvals();
        return pApprox.integrate();
    }

    private class WeightedMultiFunction implements CosScaledFunction {
        private int k;
        private MaxEntFunction2 f2;
        double[] cosValues;
        double[] f2Values;
        double[] scaledBGXs;

        private int numFuncEvals = 0;
        public int getNumFuncEvals() {
            return numFuncEvals;
        }

        public WeightedMultiFunction(int k, MaxEntFunction2 f2) {
            this.k = k;
            this.f2 = f2;
            this.numFuncEvals = 0;
        }

        @Override
        public int numFuncs() {
            return k;
        }

        private double getScaledBGX(double y) {
            double x = y * aScale + aCenter;
            double gX;
            if (isLog) {
                gX = Math.log(x);
            } else {
                gX = Math.exp(x);
            }
            double scaledBGX = (gX - bCenter) / bScale;
            return scaledBGX;
        }

        @Override
        public double[][] calc(int N) {
            if (cosValues == null) {
                cosValues = new double[N + 1];
                f2Values = new double[N + 1];
                scaledBGXs = new double[N + 1];
                for (int j = 0; j <= N; j++) {
                    cosValues[j] = FastMath.cos(j * Math.PI / N);
                    f2Values[j] = f2.value(cosValues[j]);
                    scaledBGXs[j] = getScaledBGX(cosValues[j]);
                }
                numFuncEvals += (N+1);
            } else {
                double[] oldCosValues = cosValues;
                double[] oldF2Values = f2Values;
                double[] oldScaledBGXs = scaledBGXs;
                int oldN = oldCosValues.length-1;
                int ratio = N / oldN;

                cosValues = new double[N+1];
                f2Values = new double[N+1];
                scaledBGXs = new double[N+1];
                for (int j = 0; j <= N; j++) {
                    if (j % ratio == 0) {
                        cosValues[j] = oldCosValues[j/ratio];
                        f2Values[j] = oldF2Values[j/ratio];
                        scaledBGXs[j] = oldScaledBGXs[j/ratio];
                    } else {
                        cosValues[j] = FastMath.cos(j * Math.PI / N);
                        f2Values[j] = f2.value(cosValues[j]);
                        scaledBGXs[j] = getScaledBGX(cosValues[j]);
                        numFuncEvals++;
                    }
                }
            }


            double[][] values = new double[k][N+1];
            for (int i = 0; i < k; i++) {
                for (int j = 0; j <= N; j++) {
                    values[i][j] = bases[i].value(scaledBGXs[j])*f2Values[j];
                }
            }
            return values;
        }
    }

    public double[][] getHessianNaive(double tol) {
        int ka = aCoeffs.length;
        int kb = bCoeffs.length-1;
        double[][] hess = new double[ka+kb][ka+kb];

//        UnivariateIntegrator integrator = new IterativeLegendreGaussIntegrator(4, 0, 1e-8);
        UnivariateIntegrator integrator = new RombergIntegrator(0.0, 1e-8, 4, 32);
        for (int i = 0; i < ka; i++)  {
            for (int j = 0; j <= i; j++) {
                int curI = i;
                int curJ = j;
                UnivariateFunction f = (x) -> {
                    return bases[curI].value(x)*bases[curJ].value(x)*this.value(x);
                };
                hess[i][j] = integrator.integrate(50000, f, -1, 1);
            }
        }
//        for (int i = 0; i < kb; i++) {
//            for (int j = 0; j < ka; j++) {
//                int curI = i;
//                int curJ = j;
//                UnivariateFunction f = (x) -> {
//                    return bases[curI].value(x)*bases[curJ].value(x)*this.value(x);
//                };
//                hess[i][j] = integrator.integrate(1000, f, -1, 1);
//                hess[i+ka][j] = gPolys[i].multiplyByBasis(j).multiply(cb_f).integrate();
//            }
//        }
//        for (int i = 0; i < kb; i++) {
//            for (int j = 0; j <= i; j++) {
//                hess[i+ka][j+ka] = gPolys[i].multiply(gPolys[j]).multiply(cb_f).integrate();
//            }
//        }
        for (int i=0; i<hess.length; i++) {
            for (int j=i; j<hess.length; j++) {
                hess[i][j] = hess[j][i];
            }
        }
        return hess;
    }

    public double[] getMoments(double tol) {
        WeightedMultiFunction multiFunction = new WeightedMultiFunction( bCoeffs.length, this);
        ChebyshevPolynomial[] bApproxs = ChebyshevPolynomial.fitMulti(multiFunction, tol);
        numFuncEvals += multiFunction.getNumFuncEvals();

        int k = aCoeffs.length + bCoeffs.length - 1;
        double[] singleMoments = new double[k];
        for (int i = 0; i < aCoeffs.length; i++) {
            singleMoments[i] = bApproxs[0].multiplyByBasis(i).integrate();
        }
        for (int i = 1; i < bCoeffs.length; i++) {
            singleMoments[i+aCoeffs.length-1] = bApproxs[i].integrate();
        }
//        System.out.println(Arrays.toString(singleMoments));
        return singleMoments;
    }

    public double[][] getHessian(double tol) {
        int ka = aCoeffs.length;
        int kb = bCoeffs.length-1;
        ChebyshevPolynomial cb_f = ChebyshevPolynomial.fit(this, tol);
        numFuncEvals += cb_f.getNumFitEvals();

        double[][] hess = new double[ka+kb][ka+kb];

        double[] preCalcIntegrals = new double[2*ka];
        for (int i = 0; i < 2*ka-1; i++) {
            preCalcIntegrals[i] = cb_f.multiplyByBasis(i).integrate();
        }
        for (int i = 0; i < ka; i++)  {
            for (int j = 0; j <= i; j++) {
                hess[i][j] = (preCalcIntegrals[i+j] + preCalcIntegrals[i-j])/2;
            }
        }
        for (int i = 0; i < kb; i++) {
            for (int j = 0; j < ka; j++) {
                hess[i+ka][j] = gPolys[i].multiplyByBasis(j).multiply(cb_f).integrate();
            }
        }
        for (int i = 0; i < kb; i++) {
            for (int j = 0; j <= i; j++) {
                hess[i+ka][j+ka] = gPolys[i].multiply(gPolys[j]).multiply(cb_f).integrate();
            }
        }
        for (int i=0; i<hess.length; i++) {
            for (int j=i; j<hess.length; j++) {
                hess[i][j] = hess[j][i];
            }
        }
        return hess;
    }


    public double[][] getPairwiseMoments(double tol) {
        WeightedMultiFunction multiFunction = new WeightedMultiFunction(2*bCoeffs.length-1, this);
        ChebyshevPolynomial[] bApproxs = ChebyshevPolynomial.fitMulti(multiFunction, tol);
        numFuncEvals += multiFunction.getNumFuncEvals();

        int k = aCoeffs.length + bCoeffs.length;
        double[][] pairwiseMoments = new double[k][k];
        for (int j=0; j<aCoeffs.length; j++) {
            for (int i=j; i<aCoeffs.length; i++) {
                pairwiseMoments[i][j] = (
                        bApproxs[0].multiplyByBasis(i+j).integrate()
                        + bApproxs[0].multiplyByBasis(i-j).integrate()
                ) / 2;
            }
        }
        for (int j=0; j<bCoeffs.length; j++) {
            for (int i=j; i<bCoeffs.length; i++) {
                pairwiseMoments[i+aCoeffs.length][j+aCoeffs.length] = (
                        bApproxs[i+j].integrate() + bApproxs[i-j].integrate()
                        ) / 2;
            }
        }
        for (int j=0; j<aCoeffs.length; j++) {
            for (int i=0; i<bCoeffs.length; i++) {
                pairwiseMoments[i+aCoeffs.length][j] = (
                        bApproxs[i].multiplyByBasis(j).integrate()
                );
            }
        }
        for (int j=0; j < k; j++) {
            for (int i = 0; i < j; i++) {
                pairwiseMoments[i][j] = pairwiseMoments[j][i];
            }
        }
        return pairwiseMoments;
    }

    public double[][] getHessianOld(double tol) {
        double[][] pairwiseMoments = getPairwiseMoments(tol);
        double[][] hess = new double[pairwiseMoments.length-1][pairwiseMoments.length-1];
        int numNormalPowers = aCoeffs.length;

        for (int i = 0; i < hess.length; i++) {
            for (int j = 0; j < hess.length; j++) {
                int curI = i;
                int curJ = j;
                if (curI >= numNormalPowers) {
                    curI++;
                }
                if (curJ >= numNormalPowers) {
                    curJ++;
                }
                hess[i][j] = pairwiseMoments[curI][curJ];
            }
        }
        return hess;
    }
}
