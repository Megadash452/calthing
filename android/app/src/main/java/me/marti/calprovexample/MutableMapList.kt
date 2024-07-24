package me.marti.calprovexample

/** An interface that indicates that some value `V` has a property that is used as the key `K` of a [Map].
 * Provides a method to get the value of said property.
 *
 * For example, [me.marti.calprovexample.calendar.InternalUserCalendar] implements this interface because
 * the **name** is used as the *key* in [me.marti.calprovexample.ui.MutableCalendarsList]. */
interface PropertyKey<K> {
    val key: K
}

interface ValueEditor<V> {
    /** Edits (mutates) the members of the editor to have the same values of corresponding members of [V]. */
    fun editFrom(value: V)
}

/** A collection that contains pairs of [Keys][K] to [Values][V] that also contain the key in them.
 * This means that all keys are unique, but also all values are unique.
 *
 * The collection is mutable, so elements can be *added* or *modified*, but only through an [Editor][E]
 * that only allows modification of certain members of the original [Value][V].
 * For adding elements, the [Editor][E] is also required because some members of the [Value][V] can only be obtained *after* adding the element.
 *
 * @param K An unique *key* that is mapped to a value.
 * @param V A class that itself contains the [Key][K] that it is mapped to as a member.
 * @param E A class that has all the modifiable members of the [Value][V]. */
interface MutableMapList<K, V, E>: MutableMap<K, V>, List<V>
where V: PropertyKey<K>, E: ValueEditor<V> {
    /** Change some data about an element identified with **key**.
     * @throws NoSuchElementException if there is no element with this [key]. */
    @Throws(NoSuchElementException::class)
    fun edit(key: K, editor: E.() -> Unit)

    /** Add an element to the end of the *list*.
     * @throws ElementExistsException if a calendar with the same **name** is already in the list. */
    @Throws(ElementExistsException::class)
    fun add(element: E)

    override val entries get() = MutableEntries(this)
    override val keys get() = MutableKeys(this)
    override val values get() = MutableValues(this)

    override fun get(key: K): V? {
        val index = this.indexOfFirst { cal -> cal.key == key }
        return if (index == -1)
            null
        else
            this[index]
    }
    /** ***WARNING***: Shouldn't use this. Use [edit] or [add] instead. Implemented only for compatibility.
     *
     * Adding a new *value* through this method isn't possible because an [Editor][E] needs to be constructed,
     * but a generic type can't be constructed in kotlin.
     * So using this method to [add] a new calendar will cause an exception.
     *
     * @returns the calendar before it was edited, or throws if there was no calendar.
     * @throws NoSuchElementException if there is no calendar with this name. */
    override fun put(key: K, value: V): V {
        val cal = this[key] ?: throw NoSuchElementException("Can't use .put() to add a new calendar. Use .add() instead.")
        this.edit(key) { this.editFrom(value) }
        return cal
    }
    override fun containsValue(value: V): Boolean = this.find { v -> v == value } != null
    override fun containsKey(key: K): Boolean = this.find { cal -> cal.key == key } != null
    override fun clear() { this.keys.forEach { k -> this.remove(k) } }
    override fun putAll(from: Map<out K, V>) = from.forEach { (name, cal) -> this[name] = cal }

    // -- Mutable Map class implementations

    // Mutable Map Entries

    class MutableEntry<K, V: PropertyKey<K>>(override val value: V, private val parentMap: MutableMap<K, V>): MutableMap.MutableEntry<K, V> {
        override val key: K get() = this.value.key

        override fun setValue(newValue: V): V = this.parentMap.put(this.key, newValue)!!
    }

    class MutableEntries<K, V, E>(private val parentMap: MutableMapList<K, V, E>): MutableSet<MutableMap.MutableEntry<K, V>>
    where V: PropertyKey<K>, E: ValueEditor<V> {
        override val size: Int get() = this.parentMap.size

        override fun iterator() = MutableEntriesIterator(this.parentMap)

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            if (this.contains(element))
                return false
            this.parentMap[element.key] = element.value
            return true
        }
        override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean = elements.any { this.add(it) }
        override fun clear() = this.parentMap.clear()
        override fun isEmpty(): Boolean = this.parentMap.isEmpty()
        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean = this.parentMap[element.key] == element.value
        override fun containsAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean = elements.all { this.contains(it) }
        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean = this.parentMap.remove(element.key) != null
        override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean
                = this.removeAll(this.filter { elements.contains(it) })
        override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
            var removed = false
            for ((key, _) in elements) {
                this.parentMap.remove(key)
                removed = true
            }
            return removed
        }
    }

    class MutableEntriesIterator<K, V, E>(
        private val parentMap: MutableMapList<K, V, E>
    ): MutableIterator<MutableMap.MutableEntry<K, V>>
    where V: PropertyKey<K>, E: ValueEditor<V> {
        private val iterator = this.parentMap.iterator()
        private var current: V? = null

        override fun hasNext(): Boolean = this.iterator.hasNext()
        override fun next(): MutableMap.MutableEntry<K, V> {
            val v = this.iterator.next()
            this.current = v
            return MutableEntry(v, this.parentMap)
        }
        override fun remove() {
            this.parentMap.remove(this.current?.key ?: throw IllegalStateException())
            // Function should throw if the current value has already been removed
            this.current = null
        }
    }

    // Mutable Map Keys

    class MutableKeys<K, V, E>(private val parentMap: MutableMapList<K, V, E>): MutableSet<K>
    where V: PropertyKey<K>, E: ValueEditor<V> {
        override val size: Int get() = this.parentMap.size

        override fun iterator() = MutableKeysIterator(this.parentMap)

        /** Always throws Exception because [Value][V] needs to be constructed somehow. */
        override fun add(element: K): Boolean = throw Exception("WILL NOT IMPLEMENT; Values don't have a default constructor")
        /** Always throws Exception because [Value][V] needs to be constructed somehow. */
        override fun addAll(elements: Collection<K>): Boolean = throw Exception("WILL NOT IMPLEMENT; Values don't have a default constructor")
        override fun clear() = this.parentMap.clear()
        override fun isEmpty(): Boolean = this.parentMap.isEmpty()
        override fun contains(element: K): Boolean = this.parentMap.containsKey(element)
        override fun containsAll(elements: Collection<K>): Boolean = elements.all { this.contains(it) }
        override fun remove(element: K): Boolean = this.parentMap.remove(element) != null
        override fun retainAll(elements: Collection<K>): Boolean = this.removeAll(this.filter { elements.contains(it) })
        override fun removeAll(elements: Collection<K>): Boolean {
            var removed = false
            for (key in elements) {
                this.parentMap.remove(key)
                removed = true
            }
            return removed
        }
    }

    class MutableKeysIterator<K, V, E>(private val parentMap: MutableMapList<K, V, E>): MutableIterator<K>
    where V: PropertyKey<K>, E: ValueEditor<V> {
        private val iterator = this.parentMap.iterator()
        private var current: K? = null

        override fun hasNext(): Boolean = this.iterator.hasNext()
        override fun next(): K {
            val k = this.iterator.next().key
            this.current = k
            return k
        }
        override fun remove() {
            this.parentMap.remove(this.current ?: throw IllegalStateException())
            // Function should throw if the current value has already been removed
            this.current = null
        }
    }

    // Mutable Map Values

    class MutableValues<K, V, E>(private val parentMap: MutableMapList<K, V, E>): MutableCollection<V>
    where V: PropertyKey<K>, E: ValueEditor<V> {
        override val size: Int get() = this.parentMap.size

        override fun iterator(): MutableIterator<V> = MutableValuesIterator(this.parentMap)

        override fun add(element: V): Boolean {
            if (this.contains(element))
                return false
            this.parentMap[element.key] = element
            return true
        }
        override fun addAll(elements: Collection<V>): Boolean = elements.any { this.add(it) }
        override fun clear() = this.parentMap.clear()
        override fun isEmpty(): Boolean = this.parentMap.isEmpty()
        override fun contains(element: V): Boolean = this.parentMap.contains(element)
        override fun containsAll(elements: Collection<V>): Boolean = this.parentMap.containsAll(elements)
        override fun remove(element: V): Boolean = this.parentMap.remove(element.key) != null
        override fun retainAll(elements: Collection<V>): Boolean = this.removeAll(this.filter { elements.contains(it) })
        override fun removeAll(elements: Collection<V>): Boolean {
            var removed = false
            for (value in elements) {
                this.parentMap.remove(value.key)
                removed = true
            }
            return removed
        }
    }

    class MutableValuesIterator<K, V, E>(private val parentMap: MutableMapList<K, V, E>): MutableIterator<V>
    where V: PropertyKey<K>, E: ValueEditor<V> {
        private val iterator = this.parentMap.iterator()
        private var current: V? = null

        override fun hasNext(): Boolean = this.iterator.hasNext()
        override fun next(): V {
            val v = this.iterator.next()
            this.current = v
            return v
        }
        override fun remove() {
            this.parentMap.remove(this.current?.key ?: throw IllegalStateException())
            // Function should throw if the current value has already been removed
            this.current = null
        }
    }
}