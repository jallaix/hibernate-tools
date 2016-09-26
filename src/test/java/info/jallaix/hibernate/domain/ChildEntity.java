package info.jallaix.hibernate.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;

/**
 * Created by Julien on 08/09/2016.
 */
@Entity
@Data
public class ChildEntity implements Serializable {

    @Id
    @Column
    private Integer id;

    @Column
    private String label;
}
