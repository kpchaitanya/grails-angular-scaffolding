package grails.plugin.angular.test

import javax.servlet.http.HttpServletResponse
import grails.converters.JSON
import grails.plugin.gson.converters.GSON
import grails.plugin.gson.test.GsonUnitTestMixin
import grails.test.mixin.*
import spock.lang.*
import spock.util.mop.ConfineMetaClassChanges
import static grails.plugin.gson.http.HttpConstants.SC_UNPROCESSABLE_ENTITY
import static javax.servlet.http.HttpServletResponse.*

@TestFor(AlbumController)
@Mock(Album)
@TestMixin(GsonUnitTestMixin)
@Unroll
@ConfineMetaClassChanges(HttpServletResponse)
class AlbumControllerSpec extends Specification {

    void setupSpec() {
        HttpServletResponse.metaClass.getContentAsJSON = { ->
            JSON.parse(delegate.contentAsString)
        }
    }

    void setup() {
		AlbumController.metaClass.cache = {}

        new Album(artist: 'Edward Sharpe and the Magnetic Zeroes', title: 'Here', year: '2012').save(failOnError: true, flush: true)
        new Album(artist: 'Metric', title: 'Synthetica', year: '2012').save(failOnError: true, flush: true)
        new Album(artist: 'Santigold', title: 'Master of My Make Believe', year: '2012').save(failOnError: true, flush: true)
    }

    void 'list returns JSON'() {
        when:
        controller.list()

        then:
        response.status == SC_OK

        and:
        def json = response.contentAsJSON
        json.size() == 3
        json[0].artist == 'Edward Sharpe and the Magnetic Zeroes'
        json[0].title == 'Here'
    }

    void 'list paginates with max: #max and offset: #offset'() {
        when:
        params.max = max
        params.offset = offset
        controller.list()

        then:
        def json = response.contentAsJSON
        json.size() == expectedTitles.size()

        where:
        max | offset | expectedTitles
        2   | 0      | ['Here', 'Synthetica']
        1   | 0      | ['Here']
        2   | 1      | ['Synthetica', 'Master of My Make Believe']
        2   | 2      | ['Master of My Make Believe']
    }

	void 'list returns total as response header'() {
		when:
		controller.list()

		then:
		response.getHeader('X-Pagination-Total').toInteger() == Album.count()
	}

	void 'show returns single item'() {
        when:
        params.id = Album.findByTitle('Here').id
        controller.show()

        then:
        response.status == SC_OK

        and:
        def json = response.contentAsJSON
        json.artist == 'Edward Sharpe and the Magnetic Zeroes'
        json.title == 'Here'
    }

    void 'show returns 404 when given an invalid id'() {
        when:
        params.id = 999
        controller.show()

        then:
        response.status == SC_NOT_FOUND
    }

    void 'save returns 201 status if successful'() {
        when:
        request.JSON = [artist: 'Yeasayer', title: 'Fragrant World', year: '2012'] as JSON
        controller.save()

        then:
        response.status == SC_CREATED

        and:
        Album.count() == old(Album.count()) + 1
        def album = Album.get(response.contentAsJSON.id)
        album.artist == 'Yeasayer'
        album.title == 'Fragrant World'
    }

    void 'save returns 422 if it fails'() {
        when:
        request.GSON = '{"artist": null, "title": ""}'
        controller.save()

        then:
        response.status == SC_UNPROCESSABLE_ENTITY

        and:
        def json = response.contentAsJSON
        json.errors[0] == 'Property [artist] of class [class grails.plugin.angular.test.Album] cannot be null'
        json.errors[1] == 'Property [title] of class [class grails.plugin.angular.test.Album] cannot be blank'
    }

    void 'update returns 200 if successful'() {
        when:
        params.id = Album.findByTitle('Here').id
        request.GSON = '{"artist": "Edward Sharpe & the Magnetic Zeroes", "title": "Here"}'
        controller.update()

        then:
        response.status == SC_OK

        and:
        Album.count() == old(Album.count())
        def album = Album.get(response.GSON.id.asInt)
        album.artist == 'Edward Sharpe & the Magnetic Zeroes'
        album.title == 'Here'
    }

    void 'update returns 409 if there is an optimistic lock failure'() {
        given:
        def album = Album.findByTitle('Here')

        and:
        params.id = album.id
		params.version = album.version
        request.GSON = '{"artist": "Edward Sharpe & the Magnetic Zeroes", "title": "Here"}'

        when:
        album.artist = 'Edward Sharpe & the Magnetic Zeroes'
        album.save(failOnError: true, flush: true)

        and:
        controller.update()

        then:
        album.version == old(album.version) + 1

        and:
        response.status == SC_CONFLICT

		and:
		response.contentAsJSON.errors[0] == 'Another user has updated this Album while you were editing'
    }

    void 'update returns 422 if it fails'() {
        when:
        params.id = Album.findByTitle('Here').id
        request.GSON = '{"artist": "", "title": ""}'
        controller.update()

        then:
        response.status == SC_UNPROCESSABLE_ENTITY

        and:
        def json = response.contentAsJSON
        json.errors[0] == 'Property [artist] of class [class grails.plugin.angular.test.Album] cannot be blank'
        json.errors[1] == 'Property [title] of class [class grails.plugin.angular.test.Album] cannot be blank'
    }

    void 'delete removes entity from the database'() {
        when:
        params.id = Album.findByTitle('Here').id
        controller.delete()

        then:
        response.status == SC_OK

        and:
        response.contentAsJSON.message == 'default.deleted.message'

        and:
        Album.count() == old(Album.count()) - 1
        Album.findByTitle('Here') == null
    }

    void 'delete returns 404 when given an invalid id'() {
        when:
        params.id = 999
        controller.delete()

        then:
        response.status == SC_NOT_FOUND

        and:
        Album.count() == old(Album.count())
    }
}
