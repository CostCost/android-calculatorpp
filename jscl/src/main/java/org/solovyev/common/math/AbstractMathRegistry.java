/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---------------------------------------------------------------------
 * Contact details
 *
 * Email: se.solovyev@gmail.com
 * Site:  http://se.solovyev.org
 */

package org.solovyev.common.math;

import org.solovyev.common.collections.SortedList;
import org.solovyev.common.text.Strings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * User: serso
 * Date: 9/29/11
 * Time: 4:57 PM
 */
public abstract class AbstractMathRegistry<T extends MathEntity> implements MathRegistry<T> {

    private static final MathEntityComparator<MathEntity> MATH_ENTITY_COMPARATOR = new MathEntityComparator<MathEntity>();
    @GuardedBy("this")
    @Nonnull
    private static volatile Integer counter = 0;
    @GuardedBy("this")
    @Nonnull
    protected final SortedList<T> entities = SortedList.newInstance(new ArrayList<T>(30), MATH_ENTITY_COMPARATOR);
    @GuardedBy("this")
    @Nullable
    private List<String> entityNames;
    @GuardedBy("this")
    @Nonnull
    protected final SortedList<T> systemEntities = SortedList.newInstance(new ArrayList<T>(30), MATH_ENTITY_COMPARATOR);
    private volatile boolean initialized;

    protected AbstractMathRegistry() {
    }

    @Override
    public final void init() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            onInit();
            initialized = true;
        }
    }

    protected abstract void onInit();

    @Nonnull
    private static synchronized Integer count() {
        final Integer result = counter;
        counter++;
        return result;
    }

    @Nullable
    private static <E extends MathEntity> E removeByName(@Nonnull List<E> entities, @Nonnull String name) {
        for (int i = 0; i < entities.size(); i++) {
            final E entity = entities.get(i);
            if (entity.getName().equals(name)) {
                entities.remove(i);
                return entity;
            }
        }
        return null;
    }

    private static boolean areEqual(@Nullable Object l, @Nullable Object r) {
        return l != null ? l.equals(r) : r == null;
    }

    @Nonnull
    public List<T> getEntities() {
        synchronized (this) {
            return java.util.Collections.unmodifiableList(new ArrayList<T>(entities));
        }
    }

    @Nonnull
    public List<T> getSystemEntities() {
        synchronized (this) {
            return java.util.Collections.unmodifiableList(new ArrayList<T>(systemEntities));
        }
    }

    protected void add(@Nonnull T entity) {
        synchronized (this) {
            if (entity.isSystem()) {
                if (contains(entity.getName(), this.systemEntities)) {
                    throw new IllegalArgumentException("Trying to add two system entities with same name: " + entity.getName());
                }

                this.systemEntities.add(entity);
            }

            if (!contains(entity.getName(), this.entities)) {
                addEntity(entity, this.entities);
                this.entityNames = null;
            }
        }
    }

    private void addEntity(@Nonnull T entity, @Nonnull List<T> list) {
        assert Thread.holdsLock(this);

        entity.setId(count());
        list.add(entity);
    }

    public T addOrUpdate(@Nonnull T entity) {
        synchronized (this) {
            final T existingEntity = entity.isIdDefined() ? getById(entity.getId()) : get(entity.getName());
            if (existingEntity == null) {
                addEntity(entity, entities);
                this.entityNames = null;
                if (entity.isSystem()) {
                    systemEntities.add(entity);
                }
                return entity;
            } else {
                existingEntity.copy(entity);
                this.entities.sort();
                this.entityNames = null;
                this.systemEntities.sort();
                return existingEntity;
            }
        }
    }

    public void remove(@Nonnull T entity) {
        synchronized (this) {
            if (!entity.isSystem()) {
                final T removed = removeByName(entities, entity.getName());
                if (removed != null) {
                    this.entityNames = null;
                }
            }
        }
    }

    @Nonnull
    public List<String> getNames() {
        synchronized (this) {
            if (entityNames != null) {
                return entityNames;
            }
            entityNames = new ArrayList<>(entities.size());
            for (T entity : entities) {
                final String name = entity.getName();
                if (!Strings.isEmpty(name)) {
                    entityNames.add(name);
                }
            }
            return entityNames;
        }
    }

    @Nullable
    public T get(@Nonnull final String name) {
        synchronized (this) {
            return get(name, entities);
        }
    }

    @Nullable
    private T get(@Nonnull String name, @Nonnull List<T> list) {
        for (int i = 0; i < list.size(); i++) {
            final T entity = list.get(i);
            if (areEqual(entity.getName(), name)) {
                return entity;
            }
        }
        return null;
    }

    public T getById(@Nonnull final Integer id) {
        synchronized (this) {
            for (T entity : entities) {
                if (areEqual(entity.getId(), id)) {
                    return entity;
                }
            }
            return null;
        }
    }

    public boolean contains(@Nonnull final String name) {
        synchronized (this) {
            return contains(name, this.entities);
        }
    }

    private boolean contains(final String name, @Nonnull List<T> entities) {
        return get(name, entities) != null;
    }

    static class MathEntityComparator<T extends MathEntity> implements Comparator<T> {

        MathEntityComparator() {
        }

        public int compare(T l, T r) {
            final String rName = r.getName();
            final String lName = l.getName();
            int result = rName.length() - lName.length();
            if (result == 0) {
                result = lName.compareTo(rName);
            }
            return result;
        }
    }
}
