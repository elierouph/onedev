package io.onedev.server.ci.job.param;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.ValidationException;
import javax.validation.constraints.NotNull;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.validator.constraints.NotEmpty;

import com.google.common.base.Preconditions;

import io.onedev.server.ci.job.Job;
import io.onedev.server.util.inputspec.InputSpec;
import io.onedev.server.util.inputspec.SecretInput;
import io.onedev.server.web.editable.BeanDescriptor;
import io.onedev.server.web.editable.PropertyDescriptor;

public class JobParam implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static final String PARAM_BEAN_PREFIX = "JobParamBean";
	
	private String name;
	
	private boolean secret;
	
	private ValuesProvider valuesProvider = new SpecifiedValues();

	@NotEmpty
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@NotNull
	public ValuesProvider getValuesProvider() {
		return valuesProvider;
	}

	public void setValuesProvider(ValuesProvider valuesProvider) {
		this.valuesProvider = valuesProvider;
	}

	public boolean isSecret() {
		return secret;
	}

	public void setSecret(boolean secret) {
		this.secret = secret;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof JobParam)) 
			return false;
		if (this == other)
			return true;
		JobParam otherJobParam = (JobParam) other;
		return new EqualsBuilder()
			.append(name, otherJobParam.name)
			.append(valuesProvider, otherJobParam.valuesProvider)
			.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
			.append(name)
			.append(valuesProvider)
			.toHashCode();
	}		
	
	public static void validateValues(List<List<String>> values) {
		if (values.isEmpty())
			throw new ValidationException("At least one value needs to be specified");
		Set<List<String>> encountered = new HashSet<>();
		for (List<String> value: values) {
			if (encountered.contains(value)) 
				throw new ValidationException("Duplicate values not allowed");
			else 
				encountered.add(value);
		}
	}
	
	public static void validateParams(List<InputSpec> paramSpecs, Map<String, List<List<String>>> params) {
		Map<String, InputSpec> paramSpecMap = new HashMap<>();
		for (InputSpec paramSpec: paramSpecs) {
			paramSpecMap.put(paramSpec.getName(), paramSpec);
			if (!params.containsKey(paramSpec.getName()))
				throw new ValidationException("Missing job parameter: " + paramSpec.getName());
		}
		
		for (Map.Entry<String, List<List<String>>> entry: params.entrySet()) {
			InputSpec paramSpec = paramSpecMap.get(entry.getKey());
			if (paramSpec == null)
				throw new ValidationException("Unknown job parameter: " + entry.getKey());
			
			if (entry.getValue() != null) {
				try {
					validateValues(entry.getValue());
				} catch (ValidationException e) {
					throw new ValidationException("Error validating values of parameter '" 
							+ entry.getKey() + "': " + e.getMessage());
				}
				
				for (List<String> value: entry.getValue()) {
					try {
						paramSpec.convertToObject(value);
					} catch (Exception e) {
						String displayValue;
						if (paramSpec instanceof SecretInput)
							displayValue = SecretInput.MASK;
						else
							displayValue = value.toString();
						throw new ValidationException("Error validating value '" + displayValue + "' of parameter '" 
								+ entry.getKey() + "': " + e.getMessage());
					}
				}
			}
		}
	}
	
	public static void validateParams(List<InputSpec> paramSpecs, List<JobParam> params) {
		Map<String, List<List<String>>> paramMap = new HashMap<>();
		for (JobParam param: params) {
			List<List<String>> values;
			if (param.getValuesProvider() instanceof SpecifiedValues)
				values = param.getValuesProvider().getValues();
			else
				values = null;
			if (paramMap.put(param.getName(), values) != null)
				throw new ValidationException("Duplicate param: " + param.getName());
		}
		validateParams(paramSpecs, paramMap);
	}
	
	public static Map<String, List<List<String>>> getParamMatrix(Map<String, List<String>> paramMap) {
		Map<String, List<List<String>>> paramMatrix = new HashMap<>();
		for (Map.Entry<String, List<String>> entry: paramMap.entrySet()) { 
			List<List<String>> values = new ArrayList<>();
			values.add(entry.getValue());
			paramMatrix.put(entry.getKey(), values);
		}
		return paramMatrix;
	}
	
	@SuppressWarnings("unchecked")
	public static Class<? extends Serializable> defineBeanClass(Collection<InputSpec> paramSpecs) {
		byte[] bytes = SerializationUtils.serialize((Serializable) paramSpecs);
		String className = PARAM_BEAN_PREFIX + "_" + Hex.encodeHexString(bytes);
		
		List<InputSpec> paramSpecsCopy = new ArrayList<>(paramSpecs);
		for (int i=0; i<paramSpecsCopy.size(); i++) {
			InputSpec paramSpec = paramSpecsCopy.get(i);
			if (paramSpec instanceof SecretInput) {
				InputSpec paramSpecClone = (InputSpec) SerializationUtils.clone(paramSpec);
				String description = paramSpecClone.getDescription();
				if (description == null)
					description = "";
				description += String.format("<div style='margin-top: 12px;'><b>Note:</b> Secret less than %d characters "
						+ "will not be masked in build log</div>", SecretInput.MASK.length());
				paramSpecClone.setDescription(description);
				paramSpecsCopy.set(i, paramSpecClone);
			}
		}
		return (Class<? extends Serializable>) InputSpec.defineClass(className, "Build Parameters", paramSpecsCopy);
	}
	
	@SuppressWarnings("unchecked")
	@Nullable
	public static Class<? extends Serializable> loadBeanClass(String className) {
		if (className.startsWith(PARAM_BEAN_PREFIX)) {
			byte[] bytes;
			try {
				bytes = Hex.decodeHex(className.substring(PARAM_BEAN_PREFIX.length()+1).toCharArray());
			} catch (DecoderException e) {
				throw new RuntimeException(e);
			}
			List<InputSpec> paramSpecs = (List<InputSpec>) SerializationUtils.deserialize(bytes);
			return defineBeanClass(paramSpecs);
		} else {
			return null;
		}
	}

	public static Map<String, List<String>> getParamMap(Job job, Object paramBean, Collection<String> paramNames) {
		Map<String, List<String>> paramMap = new HashMap<>();
		BeanDescriptor descriptor = new BeanDescriptor(paramBean.getClass());
		for (List<PropertyDescriptor> groupProperties: descriptor.getProperties().values()) {
			for (PropertyDescriptor property: groupProperties) {
				if (paramNames.contains(property.getDisplayName()))	{
					Object typedValue = property.getPropertyValue(paramBean);
					InputSpec paramSpec = Preconditions.checkNotNull(job.getParamSpecMap().get(property.getDisplayName()));
					List<String> values = new ArrayList<>();
					for (String value: paramSpec.convertToStrings(typedValue)) {
						if (paramSpec instanceof SecretInput)
							value = SecretInput.LITERAL_VALUE_PREFIX + value;
						values.add(value);
					}
					paramMap.put(paramSpec.getName(), values);
				}
			}
		}
		return paramMap;
	}
	
}
