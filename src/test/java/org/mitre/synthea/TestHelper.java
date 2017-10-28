package org.mitre.synthea;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;

public abstract class TestHelper {

	public static Module getFixture(String filename) throws Exception
	{
		Path modulesFolder = Paths.get("src/test/resources/generic");
		Path module = modulesFolder.resolve(filename);
		return Module.loadFile(module, modulesFolder);
	}
	
	public static void exportOff() {
		Config.set("generate.database_type", "none"); // ensure we don't write to a file-based DB
		Config.set("exporter.use_uuid_filenames", "false");
		Config.set("exporter.subfolders_by_id_substring", "false");
		Config.set("exporter.ccda.export", "false");
		Config.set("exporter.fhir.export","false");
		Config.set("exporter.hospital.fhir.export", "false");
		Config.set("exporter.cost_access_outcomes_report", "false");
	}

	public static long timestamp(int year, int month, int day, int hr, int min, int sec)
	{
		return LocalDateTime.of(year, month, day, hr, min, sec).toInstant(ZoneOffset.UTC).toEpochMilli();
	}
}
