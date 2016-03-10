package gov.hhs.cms.bluebutton.datapipeline.sampledata;

import java.time.LocalDate;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

/**
 * Models rows in the CCW's <code>CCW_BENE_CRNT_VW</code> table, which represent
 * current Medicare beneficiaries.
 */
@PersistenceCapable(table = "CCW_BENE_CRNT_VW", detachable = "true")
public class CurrentBeneficiary {
	@Persistent
	@Column(name = "BENE_ID")
	@PrimaryKey
	private Integer id;

	@Persistent
	@Column(name = "BENE_BIRTH_DT", allowsNull = "true")
	private LocalDate birthDate;

	/**
	 * Constructs a new {@link CurrentBeneficiary} instance.
	 */
	public CurrentBeneficiary() {
	}

	/**
	 * @return the beneficiary's ID
	 */
	public Integer getId() {
		return id;
	}

	/**
	 * @param id
	 *            the new value for {@link #getId()}
	 * @return this instance (for call-chaining purposes)
	 */
	public CurrentBeneficiary setId(Integer id) {
		this.id = id;
		return this;
	}

	/**
	 * @return the beneficiary's birth date
	 */
	public LocalDate getBirthDate() {
		return birthDate;
	}

	/**
	 * @param birthDate
	 *            the new value for {@link #getBirthDate()}
	 * @return this instance (for call-chaining purposes)
	 */
	public CurrentBeneficiary setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
		return this;
	}
}