package info.jallaix.hibernate.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.util.Set;

/**
 * Created by Julien on 08/09/2016.
 */
@Entity
@Data
@EqualsAndHashCode(exclude="throughEntities")
@ToString(exclude="throughEntities")
public class MainEntity {

    @Id
    @Column
    private Integer id;

    @Column
    private String label;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "mainEntity")
    private Set<ThroughEntity> throughEntities;
}
