package info.jallaix.hibernate.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

/**
 * Created by Julien on 08/09/2016.
 */
@Entity
@Data
@EqualsAndHashCode(exclude={"mainEntity", "childEntity"})
@ToString(exclude={"mainEntity", "childEntity"})
public class ThroughEntity {

    @Id
    @Column
    private Integer id;

    @Column
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mainEntityId")
    private MainEntity mainEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "childEntityId")
    private ChildEntity childEntity;
}
