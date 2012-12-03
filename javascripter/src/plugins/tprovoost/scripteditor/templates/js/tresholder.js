importClass(Packages.plugins.adufour.thresholder.Tresholder)

var seq = getSequence()

if (seq != null)
	Icy.addSequence(Thresholder.threshold(seq, 0, new double[]{127}, false))