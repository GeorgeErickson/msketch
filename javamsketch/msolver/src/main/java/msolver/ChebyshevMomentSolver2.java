package msolver;

import msolver.chebyshev.ChebyshevPolynomial;
import msolver.optimizer.BFGSOptimizer;
import msolver.optimizer.GenericOptimizer;
import msolver.optimizer.NewtonOptimizer;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolver;

import java.util.Arrays;

public class ChebyshevMomentSolver2 {
    private double[] d_mus;

    private int hessianType = 0;
    private int solverType = 0;

    private int numNormalPowers;
    private boolean useStandardBasis = true;
    private boolean verbose = false;
    private double aCenter, aScale, bCenter, bScale;

    private double[] lambdas;
    private ChebyshevPolynomial approxCDF;
    private boolean isConverged;

    private GenericOptimizer optimizer;
    private int cumFuncEvals;

    public ChebyshevMomentSolver2(
            boolean useStandardBasis,
            int numNormalPowers,
            double[] chebyshev_moments,
            double aCenter,
            double aScale,
            double bCenter,
            double bScale
    ) {
        this.useStandardBasis = useStandardBasis;
        this.numNormalPowers = numNormalPowers;
        this.d_mus = chebyshev_moments;
        this.aCenter = aCenter;
        this.aScale = aScale;
        this.bCenter = bCenter;
        this.bScale = bScale;
    }

    public static ChebyshevMomentSolver2 fromPowerSums(
            double min, double max, double[] powerSums,
            double logMin, double logMax, double[] logPowerSums
    ) {
//        double[] posPowerMoments = MathUtil.powerSumsToPosMoments(
//                powerSums, min, max
//        );
//        double[] posLogMoments = MathUtil.powerSumsToPosMoments(
//                logPowerSums, logMin, logMax
//        );
//        double powerDelta = MathUtil.deltaFromUniformMoments(posPowerMoments);
//        double logDelta = MathUtil.deltaFromUniformMoments(posLogMoments);
        double[] powerChebyMoments = MathUtil.powerSumsToChebyMoments(
                min, max, powerSums
        );
        double[] logChebyMoments = MathUtil.powerSumsToChebyMoments(
                logMin, logMax, logPowerSums
        );
        double[] powerMoments = MathUtil.powerSumsToZerodMoments(powerSums, min, max);
        double[] logMoments = MathUtil.powerSumsToZerodMoments(logPowerSums, logMin, logMax);

        // compare whether log moments or standard moments are closer to uniform distribution
        double powerDelta = MathUtil.deltaFromUniformZerodMoments(powerMoments);
        double logDelta = MathUtil.deltaFromUniformZerodMoments(logMoments);
        int powerSmall = MathUtil.numSmallerThan(powerChebyMoments, 1.1);
        int logSmall = MathUtil.numSmallerThan(logChebyMoments, 1.1);

//        System.out.println(Arrays.toString(powerMoments));
//        System.out.println(Arrays.toString(logMoments));
//        System.out.println(String.format("%g,%g,%s", min, max, Arrays.toString(powerSums)));
//        System.out.println(String.format("%g,%g,%s", logMin, logMax, Arrays.toString(logPowerSums)));
//        System.out.println("power delta: "+powerDelta+", logDelta: "+logDelta);

        boolean useStandardBasis = true;
        if (logSmall >= powerSmall) {
            if (logDelta < powerDelta) {
                useStandardBasis = false;
            }
        }

        double[] aMoments, bMoments;
        double aMin, aMax, bMin, bMax;
        if (useStandardBasis) {
            aMoments = powerChebyMoments;
            bMoments = logChebyMoments;
            aMin = min; aMax = max; bMin = logMin; bMax = logMax;
        } else {
            aMoments = logChebyMoments;
            bMoments = powerChebyMoments;
            bMin = min; bMax = max; aMin = logMin; aMax = logMax;
        }

        double aCenter = (aMax + aMin)/2;
        double aScale = (aMax - aMin)/2;
        double bCenter = (bMax + bMin)/2;
        double bScale = (bMax - bMin)/2;

        // Don't use all of the secondary powers to solve, the acc / speed tradeoff
        // isn't worth it.
        SolveBasisSelector sel = new SolveBasisSelector();
        sel.select(useStandardBasis, aMoments, bMoments, aCenter, aScale, bCenter, bScale);
        int ka = sel.getKa();
        int kb = sel.getKb();
        aMoments = Arrays.copyOf(aMoments, ka);
        bMoments = Arrays.copyOf(bMoments, kb);

//        System.out.println(aMin+","+aMax+","+Arrays.toString(aMoments)+","+Arrays.toString(bMoments));

        double[] combinedMoments = new double[aMoments.length + bMoments.length - 1];
        for (int i = 0; i < aMoments.length; i++) {
            combinedMoments[i] = aMoments[i];
        }
        for (int i = 0; i < bMoments.length - 1; i++) {
            combinedMoments[i + aMoments.length] = bMoments[i + 1];
        }
        return new ChebyshevMomentSolver2(
                useStandardBasis,
                aMoments.length,
                combinedMoments,
                aCenter,
                aScale,
                bCenter,
                bScale
        );
    }

    public void setVerbose(boolean flag) {
        this.verbose = flag;
    }

    public int solve(double tol) {
        double[] l_initial = new double[d_mus.length];
        return solve(l_initial, tol);
    }

    public int solve(double[] l_initial, double tol) {
        MaxEntPotential2 potential = new MaxEntPotential2(
                useStandardBasis,
                numNormalPowers,
                d_mus,
                aCenter,
                aScale,
                bCenter,
                bScale
        );
        potential.setHessianType(hessianType);
        if (solverType == 1) {
            optimizer = new BFGSOptimizer(potential);
            optimizer.setMaxIter(5000);
        } else {
            optimizer = new NewtonOptimizer(potential);
        }
        optimizer.setVerbose(verbose);
        if (verbose) {
            System.out.println("Beginning solve with order: "+numNormalPowers+","+(d_mus.length-numNormalPowers+1));
            System.out.println("Using hessian type: "+hessianType);
        }

        lambdas = optimizer.solve(l_initial, tol);
        isConverged = optimizer.isConverged();
        cumFuncEvals = potential.getCumFuncEvals();
        if (verbose) {
            System.out.println("Using standard basis: "+ useStandardBasis);
            System.out.println("Final Polynomial: " + Arrays.toString(lambdas));
            System.out.println("Total Function Evals: "+cumFuncEvals);
            System.out.println(String.format("linscales: "+ aCenter +","+aScale+","+bCenter+","+bScale));
        }

        approxCDF = ChebyshevPolynomial.fit(potential.getFunc(), tol).integralPoly();
        return optimizer.getStepCount();
    }

    public double[] estimateQuantiles(double[] ps) {
        UnivariateSolver bSolver = new BrentSolver(1e-6);
        int n = ps.length;
        double[] quantiles = new double[n];

        for (int i = 0; i < n; i++) {
            double p = ps[i];
            double q;

            double pMax = approxCDF.value(1);
            double pAdj = p * pMax;
            if (pAdj <= 0) {
                q = -1;
            } else if (pAdj >= pMax) {
                q = 1;
            } else {
                q = bSolver.solve(
                        100,
                        (x) -> approxCDF.value(x) - pAdj,
                        -1,
                        1,
                        0
                );
            }
            quantiles[i] = q*aScale+aCenter;
            if (!useStandardBasis) {
                quantiles[i] = Math.exp(quantiles[i]);
            }
        }
        return quantiles;
    }

    public int getK1() {
        return numNormalPowers;
    }
    public int getK2() {
        return d_mus.length - numNormalPowers + 1;
    }

    public double estimateCDF(double x) {
        double y;
        if (useStandardBasis) {
            y = (x - aCenter) / aScale;
        } else {
            y = (Math.log(x) - aCenter) / aScale;
        }
        return approxCDF.value(y);
    }

    public double[] getLambdas() {
        return lambdas;
    }

    public GenericOptimizer getOptimizer() {
        return optimizer;
    }
    public int getCumFuncEvals() {
        return cumFuncEvals;
    }
    public double[] getChebyshevMoments() { return d_mus; }

    public boolean isConverged() {
        return isConverged;
    }
    public boolean isUseStandardBasis() {
        return useStandardBasis;
    }
    public int getNumNormalPowers() {
        return numNormalPowers;
    }

    public void setHessianType(int hessianType) {
        this.hessianType = hessianType;
    }
    public void setSolverType(int solverType) {
        this.solverType = solverType;
    }
}
