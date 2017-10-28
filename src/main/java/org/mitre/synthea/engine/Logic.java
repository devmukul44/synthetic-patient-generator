package org.mitre.synthea.engine;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Logic represents any portion of a generic module that requires a logical
 * expression. This class is stateless, and calling 'test' on an instance
 * must not modify state as instances of Logic within Modules are shared 
 * across the population.
 */
public class Logic {

	public enum ConditionType {
		GENDER("Gender"), 
		SOCIOECONOMIC_STATUS("Socioeconomic Status"),
		RACE("Race"),
		AGE("Age"),
		DATE("Date"),
		SYMPTOM("Symptom"),
		OBSERVATION("Observation"),
		VITAL_SIGN("Vital Sign"),
		ACTIVE_CONDITION("Active Condition"),
		ACTIVE_MEDICATION("Active Medication"),
		ACTIVE_CAREPLAN("Active CarePlan"),
		ATTRIBUTE("Attribute"),
		PRIOR_STATE("PriorState"),
		AND("And"),
		OR("Or"),
		NOT("Not"),
		AT_LEAST("At Least"),
		AT_MOST("At Most"),
		TRUE("True"),
		FALSE("False");
		
		private String text;
		
		ConditionType(String text) {
			this.text = text;
		}
		
		public static ConditionType fromString(String text) {
			for(ConditionType type : ConditionType.values()) {
				if(type.text.equalsIgnoreCase(text)) {
					return type;
				}
			}
			return ConditionType.valueOf(text);
		}
	}
	
	public ConditionType type;
	public JsonObject definition;
	
	public Logic(JsonObject definition) {
		this.type = ConditionType.fromString( definition.get("condition_type").getAsString() );
		this.definition = definition;
		// TODO - make Logic OO like States. 
		// don't forget about remarks
	}
	
	public boolean test(Person person, long time) {
		switch(type) {
		case GENDER:
			String gender = definition.get("gender").getAsString();
			return gender.equals(person.attributes.get(Person.GENDER));
		case AGE:
			long age;
			String operator = definition.get("operator").getAsString();
			long quantity = definition.get("quantity").getAsLong();
			String units = definition.get("unit").getAsString();
			
			switch (units)
			{
			case "years":
				age = person.ageInYears(time);
				break;
			case "months":
				age = person.ageInMonths(time);
				break;
			default:
				// TODO - add more unit types if we determine they are necessary
				throw new UnsupportedOperationException("Units '" + units + "' not currently supported in Age logic.");
			}

			return Utilities.compare((double)age, (double)quantity, operator);
		case DATE:
			operator = definition.get("operator").getAsString();
			quantity = definition.get("year").getAsLong();
			int year = Utilities.getYear(time);
			return Utilities.compare(year, (double)quantity, operator);
		case SOCIOECONOMIC_STATUS:
			String category = definition.get("category").getAsString();
			return category.equals(person.attributes.get(Person.SOCIOECONOMIC_CATEGORY));
		case RACE:
			String race = definition.get("race").getAsString();
			return race.equals(person.attributes.get(Person.RACE));
		case SYMPTOM:
			String symptom = definition.get("symptom").getAsString();
			operator = definition.get("operator").getAsString();
			double value = definition.get("value").getAsDouble();
			return Utilities.compare((double)person.getSymptom(symptom), value, operator);
		case OBSERVATION:
			operator = definition.get("operator").getAsString();
			Observation observation = null;
			if(definition.has("codes")) {
				for(JsonElement item : definition.get("codes").getAsJsonArray()) {
					Code code = new Code((JsonObject) item);
					Observation last = person.record.getLatestObservation(code.code);
					if(last != null) {
						observation = last;
						break;
					}
				}
			}  else if(definition.has("referenced_by_attribute")) {
				String attribute = definition.get("referenced_by_attribute").getAsString();
				if(person.attributes.containsKey(attribute)) {
					observation = (Observation) person.attributes.get(attribute);
				} else {
					return false;
				}
			}
			if(definition.has("value")) {
				value = definition.get("value").getAsDouble();
				return Utilities.compare(observation.value, value, operator);
			} else {
				return Utilities.compare(observation.value, null, operator);
			}
		case ATTRIBUTE:
			String attribute = definition.get("attribute").getAsString();
			operator = definition.get("operator").getAsString();
			if(definition.has("value")) {
				Object val = Utilities.primitive(definition.get("value").getAsJsonPrimitive());
				if(val instanceof String) {
					return val.equals(person.attributes.get(attribute));
				} else {
					return Utilities.compare(person.attributes.get(attribute), val, operator);
				}				
			} else {
				return Utilities.compare(person.attributes.get(attribute), null, operator);
			}
		case AND:
			JsonArray conditions = definition.get("conditions").getAsJsonArray();
			boolean allTrue = true;
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				allTrue = allTrue && condition.test(person, time);
			}
			return allTrue;
		case OR:
			conditions = definition.get("conditions").getAsJsonArray();
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				if(condition.test(person, time)) {
					return true;
				}
			}
			return false;
		case AT_LEAST:
			int count = 0;
			int minimum = definition.get("minimum").getAsInt();
			conditions = definition.get("conditions").getAsJsonArray();
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				if(condition.test(person, time)) {
					count++;
				}
			}
			return count >= minimum;
		case AT_MOST:
			count = 0;
			int maximum = definition.get("maximum").getAsInt();
			conditions = definition.get("conditions").getAsJsonArray();
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				if(condition.test(person, time)) {
					count++;
				}
			}
			return count <= maximum;
		case NOT:
			JsonObject not = definition.get("condition").getAsJsonObject();
			Logic condition = new Logic(not);
			return !condition.test(person, time);
		case TRUE:
			return true;
		case FALSE:
			return false;
		case PRIOR_STATE:
			String priorStateName = definition.get("name").getAsString();
			String priorStateSince = null;
			Long sinceTime = null;
			if(definition.has("since")) {
				priorStateSince = definition.get("since").getAsString();
			} 
			if(definition.has("within")) {
				units = definition.get("within").getAsJsonObject().get("unit").getAsString();
				quantity = definition.get("within").getAsJsonObject().get("quantity").getAsLong();
				long window = Utilities.convertTime(units, quantity);
				sinceTime = time - window;
			}
				
			return person.hadPriorState(priorStateName, priorStateSince, sinceTime);
			
		case ACTIVE_CONDITION:
			if(definition.has("codes")) {
				for(JsonElement item : definition.get("codes").getAsJsonArray()) {
					Code code = new Code((JsonObject) item);
					if(person.record.present.containsKey(code.code)) {
						return true;
					}
				}
				return false;
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				if(person.attributes.containsKey(attribute)) {
					Entry diagnosis = (Entry) person.attributes.get(attribute);
					return person.record.present.containsKey(diagnosis.type);
				} else {
					return false;
				}
			}
		case ACTIVE_MEDICATION:
			if(definition.has("codes")) {
				for(JsonElement item : definition.get("codes").getAsJsonArray()) {
					Code code = new Code((JsonObject) item);
					if(person.record.medicationActive(code.code)) {
						return true;
					}
				}
				return false;
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				if(person.attributes.containsKey(attribute)) {
					Medication medication = (Medication) person.attributes.get(attribute);
					return person.record.medicationActive(medication.type);
				} else {
					return false;
				}
			}
		case ACTIVE_CAREPLAN:
			if(definition.has("codes")) {
				for(JsonElement item : definition.get("codes").getAsJsonArray()) {
					Code code = new Code((JsonObject) item);
					if(person.record.careplanActive(code.code)) {
						return true;
					}
				}
				return false;
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				if(person.attributes.containsKey(attribute)) {
					CarePlan carePlan = (CarePlan) person.attributes.get(attribute);
					return person.record.careplanActive(carePlan.type);
				} else {
					return false;
				}
			}
		case VITAL_SIGN:
			String vitalSignName = definition.get("vital_sign").getAsString();
			VitalSign vs = VitalSign.fromString(vitalSignName);
			operator = definition.get("operator").getAsString();
			value = definition.get("value").getAsDouble();
			return Utilities.compare(person.getVitalSign(vs), value, operator);
		default:
			System.err.format("Unhandled Logic: %s\n", type);
			return false;
		}
	}
}
